package com.connecta.backend.meta.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.connecta.backend.config.MetaProperties;
import com.connecta.backend.meta.domain.MetaAuthorization;
import com.connecta.backend.meta.repository.MetaAuthorizationRepository;
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
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MetaAuthorizationServiceTests {

    private static final String KEY = "dGVzdC1vbmx5LW1ldGEtdG9rZW4ta2V5LTAwMDAwMDE=";
    private static final String BASE = "https://api.example.com";
    private static final String REDIRECT = BASE + MetaAuthorizationService.CALLBACK_PATH;
    private static final Clock CLOCK = TestClock.FIXED;
    private static final Instant NOW = CLOCK.instant();

    private MetaProperties properties;
    private OAuthStateSigner states;
    private MetaOAuthClient meta;
    private TokenCipher cipher;
    private TenantAccessService tenants;
    private ActiveCaller callers;
    private MetaAuthorizationRepository authorizations;
    private MetaAuthorizationService service;

    private Organization acme;
    private User admin;

    @BeforeEach
    void setUp() {
        properties = new MetaProperties("app-id", "app-secret", null, "config-id", BASE, KEY);
        states = new OAuthStateSigner(properties);
        meta = mock(MetaOAuthClient.class);
        cipher = new TokenCipher(properties);
        tenants = mock(TenantAccessService.class);
        callers = mock(ActiveCaller.class);
        authorizations = mock(MetaAuthorizationRepository.class);
        service = new MetaAuthorizationService(properties, states, meta, cipher, tenants, callers,
                authorizations, CLOCK);

        acme = new Organization("Acme", "acme");
        TestUsers.set(acme, "id", UUID.randomUUID());
        admin = TestUsers.user("admin@example.com", Roles.ADMIN);
    }

    private Membership membership(String role) {
        return new Membership(acme, admin, new Role(role));
    }

    @Nested
    class Start {

        @Test
        void anAdminGetsTheMetaLoginUrlWithTheCallbackAsRedirect() {
            when(callers.load("sub")).thenReturn(admin);
            when(tenants.requireRole(acme.getId(), admin.getId(), Roles.ADMIN)).thenReturn(membership(Roles.ADMIN));
            when(meta.authorizationUrl(anyString(), eq(REDIRECT))).thenReturn("https://dialog");

            assertEquals("https://dialog", service.startAuthorization("sub", acme.getId()));
        }

        @Test
        void anAgentIsRefused() {
            when(callers.load("sub")).thenReturn(admin);
            when(tenants.requireRole(acme.getId(), admin.getId(), Roles.ADMIN))
                    .thenThrow(new InsufficientOrganizationRoleException());

            assertThrows(InsufficientOrganizationRoleException.class,
                    () -> service.startAuthorization("sub", acme.getId()));
            verify(meta, never()).authorizationUrl(anyString(), anyString());
        }

        @Test
        void aNonMemberIsNotFound() {
            when(callers.load("sub")).thenReturn(admin);
            when(tenants.requireRole(acme.getId(), admin.getId(), Roles.ADMIN)).thenThrow(new NotAMemberException());

            assertThrows(NotAMemberException.class, () -> service.startAuthorization("sub", acme.getId()));
        }

        @Test
        void withoutMetaSettingsItIsNotConfigured() {
            MetaProperties unset = new MetaProperties("", "", null, "", "", KEY);
            MetaAuthorizationService unconfigured = new MetaAuthorizationService(unset,
                    new OAuthStateSigner(unset), meta, cipher, tenants, callers, authorizations, CLOCK);
            when(callers.load("sub")).thenReturn(admin);
            when(tenants.requireRole(acme.getId(), admin.getId(), Roles.ADMIN)).thenReturn(membership(Roles.ADMIN));

            assertThrows(MetaNotConfiguredException.class,
                    () -> unconfigured.startAuthorization("sub", acme.getId()));
        }
    }

    @Nested
    class Complete {

        @Test
        void storesTheLongLivedTokenEncryptedForTheOrganization() {
            String state = states.issue(acme.getId(), admin.getId(), NOW);
            Instant expires = NOW.plusSeconds(5_184_000);
            when(callers.load(admin.getId().toString())).thenReturn(admin);
            when(tenants.requireRole(acme.getId(), admin.getId(), Roles.ADMIN)).thenReturn(membership(Roles.ADMIN));
            when(meta.exchangeCode("code", REDIRECT)).thenReturn(new MetaUserToken("short", NOW));
            when(meta.extendToken("short")).thenReturn(new MetaUserToken("long-token", expires));
            when(authorizations.findByOrganizationId(acme.getId())).thenReturn(Optional.empty());
            when(authorizations.save(any(MetaAuthorization.class))).thenAnswer(call -> call.getArgument(0));

            MetaAuthorization saved = service.completeAuthorization("code", state);

            assertEquals("long-token", cipher.decrypt(saved.getAccessTokenEncrypted()));
            assertFalse(saved.getAccessTokenEncrypted().contains("long-token"));
            assertEquals(expires, saved.getExpiresAt());
            assertEquals(admin, saved.getGrantedBy());
            assertEquals(acme.getId(), saved.getOrganization().getId());
        }

        @Test
        void reconnectingReplacesTheExistingGrantInsteadOfAddingOne() {
            String state = states.issue(acme.getId(), admin.getId(), NOW);
            MetaAuthorization existing = new MetaAuthorization(acme);
            existing.replaceToken(cipher.encrypt("old"), admin, null, NOW.minusSeconds(60));
            when(callers.load(admin.getId().toString())).thenReturn(admin);
            when(tenants.requireRole(acme.getId(), admin.getId(), Roles.ADMIN)).thenReturn(membership(Roles.ADMIN));
            when(meta.exchangeCode(anyString(), anyString())).thenReturn(new MetaUserToken("short", NOW));
            when(meta.extendToken("short")).thenReturn(new MetaUserToken("fresh", null));
            when(authorizations.findByOrganizationId(acme.getId())).thenReturn(Optional.of(existing));
            when(authorizations.save(any(MetaAuthorization.class))).thenAnswer(call -> call.getArgument(0));

            MetaAuthorization saved = service.completeAuthorization("code", state);

            assertSame(existing, saved);
            assertEquals("fresh", cipher.decrypt(saved.getAccessTokenEncrypted()));
            ArgumentCaptor<MetaAuthorization> captor = ArgumentCaptor.forClass(MetaAuthorization.class);
            verify(authorizations).save(captor.capture());
            assertSame(existing, captor.getValue());
            assertNotEquals("old", cipher.decrypt(saved.getAccessTokenEncrypted()));
        }

        @Test
        void aTamperedStateIsRejectedBeforeMetaIsCalled() {
            assertThrows(InvalidOAuthStateException.class, () -> service.completeAuthorization("code", "garbage"));
            verify(meta, never()).exchangeCode(anyString(), anyString());
        }

        @Test
        void anAdminWhoLostTheRoleSinceStartingIsRefused() {
            String state = states.issue(acme.getId(), admin.getId(), NOW);
            when(callers.load(admin.getId().toString())).thenReturn(admin);
            when(tenants.requireRole(acme.getId(), admin.getId(), Roles.ADMIN))
                    .thenThrow(new InsufficientOrganizationRoleException());

            assertThrows(InsufficientOrganizationRoleException.class,
                    () -> service.completeAuthorization("code", state));
            verify(meta, never()).exchangeCode(anyString(), anyString());
            verify(authorizations, never()).save(any());
        }

        @Test
        void whenMetaRefusesNothingIsSaved() {
            String state = states.issue(acme.getId(), admin.getId(), NOW);
            when(callers.load(admin.getId().toString())).thenReturn(admin);
            when(tenants.requireRole(acme.getId(), admin.getId(), Roles.ADMIN)).thenReturn(membership(Roles.ADMIN));
            when(meta.exchangeCode(anyString(), anyString()))
                    .thenThrow(new MetaAuthorizationFailedException("Meta token request failed"));

            assertThrows(MetaAuthorizationFailedException.class,
                    () -> service.completeAuthorization("code", state));
            verify(authorizations, never()).save(any());
        }
    }

    @Nested
    class Status {

        @Test
        void anOrganizationThatNeverConnectedHasNoGrant() {
            when(callers.load("sub")).thenReturn(admin);
            when(tenants.requireRole(acme.getId(), admin.getId(), Roles.ADMIN)).thenReturn(membership(Roles.ADMIN));
            when(authorizations.findByOrganizationId(acme.getId())).thenReturn(Optional.empty());

            assertFalse(service.authorization("sub", acme.getId()).isPresent());
            assertTrue(service.authorization("sub", acme.getId()).isEmpty());
        }
    }
}
