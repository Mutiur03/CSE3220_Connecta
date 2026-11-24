package com.connecta.backend.meta.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import org.springframework.context.ApplicationEventPublisher;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.connecta.backend.config.InstagramProperties;
import com.connecta.backend.config.MetaProperties;
import com.connecta.backend.meta.domain.MetaConnectedAccount;
import com.connecta.backend.meta.repository.MetaConnectedAccountRepository;
import com.connecta.backend.meta.service.InstagramOAuthClient.InstagramAccount;
import com.connecta.backend.meta.service.MetaOAuthClient.MetaUserToken;
import com.connecta.backend.org.domain.Membership;
import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.org.service.ActiveCaller;
import com.connecta.backend.org.service.InsufficientOrganizationRoleException;
import com.connecta.backend.org.service.NotAMemberException;
import com.connecta.backend.org.service.TenantAccessService;
import com.connecta.backend.support.TestClock;
import com.connecta.backend.support.TestUsers;
import com.connecta.backend.user.domain.Role;
import com.connecta.backend.user.domain.Roles;
import com.connecta.backend.user.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class InstagramConnectServiceTests {

    private static final String KEY = "dGVzdC1vbmx5LW1ldGEtdG9rZW4ta2V5LTAwMDAwMDE=";
    private static final String BASE = "https://api.example.com";
    private static final String REDIRECT = BASE + InstagramConnectService.CALLBACK_PATH;

    private MetaProperties meta;
    private OAuthStateSigner states;
    private InstagramOAuthClient client;
    private TokenCipher cipher;
    private TenantAccessService tenants;
    private ActiveCaller callers;
    private MetaConnectedAccountRepository accounts;
    private ApplicationEventPublisher publisher;
    private InstagramConnectService service;

    private Organization acme;
    private User admin;

    @BeforeEach
    void setUp() {
        meta = new MetaProperties("fb-app", "fb-secret", null, "config", BASE, KEY);
        states = new OAuthStateSigner(meta);
        client = mock(InstagramOAuthClient.class);
        cipher = new TokenCipher(meta);
        tenants = mock(TenantAccessService.class);
        callers = mock(ActiveCaller.class);
        accounts = mock(MetaConnectedAccountRepository.class);
        publisher = mock(ApplicationEventPublisher.class);
        service = new InstagramConnectService(meta, new InstagramProperties("ig-app", "ig-secret"), states, client,
                cipher, tenants, callers, accounts, TestClock.FIXED, publisher);

        acme = new Organization("Acme", "acme");
        TestUsers.set(acme, "id", UUID.randomUUID());
        admin = TestUsers.user("admin@example.com", Roles.ADMIN);
    }

    private Membership membership(String role) {
        return new Membership(acme, admin, new Role(role));
    }

    private void adminIsAMember() {
        when(callers.load("sub")).thenReturn(admin);
        when(callers.load(admin.getId().toString())).thenReturn(admin);
        when(tenants.requireRole(acme.getId(), admin.getId(), Roles.ADMIN)).thenReturn(membership(Roles.ADMIN));
    }

    @Nested
    class Start {

        @Test
        void anAdminGetsTheInstagramLoginUrlWithTheCallbackAsRedirect() {
            adminIsAMember();
            when(client.authorizationUrl(anyString(), eq(REDIRECT))).thenReturn("https://ig-login");

            assertEquals("https://ig-login", service.startConnection("sub", acme.getId()));
        }

        @Test
        void aNonMemberIsNotFound() {
            when(callers.load("sub")).thenReturn(admin);
            when(tenants.requireRole(acme.getId(), admin.getId(), Roles.ADMIN)).thenThrow(new NotAMemberException());

            assertThrows(NotAMemberException.class, () -> service.startConnection("sub", acme.getId()));
        }

        @Test
        void anAgentIsForbidden() {
            when(callers.load("sub")).thenReturn(admin);
            when(tenants.requireRole(acme.getId(), admin.getId(), Roles.ADMIN))
                    .thenThrow(new InsufficientOrganizationRoleException());

            assertThrows(InsufficientOrganizationRoleException.class,
                    () -> service.startConnection("sub", acme.getId()));
            verify(client, never()).authorizationUrl(anyString(), anyString());
        }
    }

    @Nested
    class Complete {

        @Test
        void storesTheAccountWithItsTokenEncryptedAndTheAdminWhoConnectedIt() {
            String state = states.issue(acme.getId(), admin.getId(), TestClock.NOW);
            adminIsAMember();
            when(client.exchangeCode("code", REDIRECT)).thenReturn(new MetaUserToken("short", TestClock.NOW));
            when(client.extendToken("short")).thenReturn(new MetaUserToken("long-token", null));
            when(client.fetchAccount("long-token")).thenReturn(new InstagramAccount("17841", "acme_shop"));
            when(accounts.findByOrganizationIdAndPlatformAndExternalAccountId(
                    acme.getId(), MetaConnectedAccount.PLATFORM_INSTAGRAM, "17841")).thenReturn(Optional.empty());
            when(accounts.save(any(MetaConnectedAccount.class))).thenAnswer(call -> call.getArgument(0));

            MetaConnectedAccount saved = service.completeConnection("code", state);

            verify(publisher).publishEvent(new ChannelConnectedEvent(
                    com.connecta.backend.inbox.domain.Channel.INSTAGRAM, saved.getId()));
            assertEquals("17841", saved.getExternalAccountId());
            assertEquals("acme_shop", saved.getUsername());
            assertEquals(admin, saved.getGrantedBy());
            assertEquals("long-token", cipher.decrypt(saved.getAccessTokenEncrypted()));
            assertFalse(saved.getAccessTokenEncrypted().contains("long-token"));
        }

        @Test
        void reconnectingRefreshesTheSameAccountRow() {
            String state = states.issue(acme.getId(), admin.getId(), TestClock.NOW);
            MetaConnectedAccount existing = new MetaConnectedAccount(acme, MetaConnectedAccount.PLATFORM_INSTAGRAM,
                    "17841");
            existing.refresh("old_name", cipher.encrypt("old"), admin, TestClock.NOW);
            adminIsAMember();
            when(client.exchangeCode(anyString(), anyString())).thenReturn(new MetaUserToken("short", TestClock.NOW));
            when(client.extendToken("short")).thenReturn(new MetaUserToken("fresh", null));
            when(client.fetchAccount("fresh")).thenReturn(new InstagramAccount("17841", "new_name"));
            when(accounts.findByOrganizationIdAndPlatformAndExternalAccountId(
                    acme.getId(), MetaConnectedAccount.PLATFORM_INSTAGRAM, "17841")).thenReturn(Optional.of(existing));
            when(accounts.save(any(MetaConnectedAccount.class))).thenAnswer(call -> call.getArgument(0));

            MetaConnectedAccount saved = service.completeConnection("code", state);

            assertSame(existing, saved);
            assertEquals("new_name", saved.getUsername());
            assertEquals("fresh", cipher.decrypt(saved.getAccessTokenEncrypted()));
        }

        @Test
        void aTamperedStateIsRejectedBeforeInstagramIsCalled() {
            assertThrows(InvalidOAuthStateException.class, () -> service.completeConnection("code", "garbage"));
            verify(client, never()).exchangeCode(anyString(), anyString());
        }

        @Test
        void anAdminWhoLostTheRoleIsRefusedAndNothingIsExchanged() {
            String state = states.issue(acme.getId(), admin.getId(), TestClock.NOW);
            when(callers.load(admin.getId().toString())).thenReturn(admin);
            when(tenants.requireRole(acme.getId(), admin.getId(), Roles.ADMIN))
                    .thenThrow(new InsufficientOrganizationRoleException());

            assertThrows(InsufficientOrganizationRoleException.class,
                    () -> service.completeConnection("code", state));
            verify(client, never()).exchangeCode(anyString(), anyString());
            verify(accounts, never()).save(any());
        }

        @Test
        void whenInstagramRefusesNothingIsSaved() {
            String state = states.issue(acme.getId(), admin.getId(), TestClock.NOW);
            adminIsAMember();
            when(client.exchangeCode(anyString(), anyString()))
                    .thenThrow(new MetaAuthorizationFailedException("Instagram request failed"));

            assertThrows(MetaAuthorizationFailedException.class, () -> service.completeConnection("code", state));
            verify(accounts, never()).save(any());
        }
    }

    @Nested
    class Connections {

        @Test
        void listsOnlyTheInstagramAccountsOfTheOrganization() {
            adminIsAMember();
            when(accounts.findByOrganizationIdAndPlatform(eq(acme.getId()), eq("INSTAGRAM"), any()))
                    .thenReturn(org.springframework.data.domain.Page.empty());

            assertFalse(service.connections("sub", acme.getId(),
                    org.springframework.data.domain.PageRequest.of(0, 20)).hasContent());
        }
    }

    @Nested
    class Cancelled {

        @Test
        void aCancelledLoginIsDeniedAndInstagramIsNotCalled() {
            adminIsAMember();
            String state = states.issue(acme.getId(), admin.getId(), TestClock.NOW);

            assertThrows(MetaAuthorizationDeniedException.class, () -> service.completeConnection(null, state));
            verify(client, never()).exchangeCode(anyString(), anyString());
        }
    }
}
