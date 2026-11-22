package com.connecta.backend.meta.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.connecta.backend.config.MetaProperties;
import com.connecta.backend.meta.domain.MetaAuthorization;
import com.connecta.backend.meta.domain.MetaConnectedPage;
import com.connecta.backend.meta.repository.MetaAuthorizationRepository;
import com.connecta.backend.meta.repository.MetaConnectedPageRepository;
import com.connecta.backend.meta.service.MetaOAuthClient.MetaPage;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

class MetaPageServiceTests {

    private static final String KEY = "dGVzdC1vbmx5LW1ldGEtdG9rZW4ta2V5LTAwMDAwMDE=";
    private static final Clock CLOCK = TestClock.FIXED;
    private static final Instant NOW = CLOCK.instant();
    private static final String USER_TOKEN = "user-token";

    private MetaAuthorizationRepository authorizations;
    private MetaConnectedPageRepository pages;
    private MetaOAuthClient meta;
    private TokenCipher cipher;
    private TenantAccessService tenants;
    private ActiveCaller callers;
    private MetaPageService service;

    private Organization acme;
    private User admin;

    @BeforeEach
    void setUp() {
        MetaProperties properties = new MetaProperties("app-id", "app-secret", null, "config-id",
                "https://api.example.com", KEY);
        authorizations = mock(MetaAuthorizationRepository.class);
        pages = mock(MetaConnectedPageRepository.class);
        meta = mock(MetaOAuthClient.class);
        cipher = new TokenCipher(properties);
        tenants = mock(TenantAccessService.class);
        callers = mock(ActiveCaller.class);
        service = new MetaPageService(properties, authorizations, pages, meta, cipher, tenants, callers, CLOCK);

        acme = new Organization("Acme", "acme");
        TestUsers.set(acme, "id", UUID.randomUUID());
        admin = TestUsers.user("admin@example.com", Roles.ADMIN);
        when(callers.load("sub")).thenReturn(admin);
        when(tenants.requireRole(acme.getId(), admin.getId(), Roles.ADMIN))
                .thenReturn(new Membership(acme, admin, new Role(Roles.ADMIN)));
    }

    /** A stored grant whose user token is encrypted, plus the two Pages Meta returns for it. */
    private void grantTwoPages() {
        MetaAuthorization grant = new MetaAuthorization(acme);
        grant.replaceToken(cipher.encrypt(USER_TOKEN), admin, null, NOW);
        when(authorizations.findByOrganizationId(acme.getId())).thenReturn(Optional.of(grant));
        when(meta.listPages(USER_TOKEN)).thenReturn(List.of(
                new MetaPage("p1", "Page One", "page-token-1"),
                new MetaPage("p2", "Page Two", "page-token-2")));
        when(pages.save(any(MetaConnectedPage.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Nested
    class Connect {

        @Test
        void subscribesTheChosenPageAndStoresItsTokenEncrypted() {
            grantTwoPages();
            when(pages.findByOrganizationIdAndPageId(acme.getId(), "p1")).thenReturn(Optional.empty());

            List<MetaConnectedPage> connected = service.connect("sub", acme.getId(), List.of("p1"));

            verify(meta).subscribePage("p1", "page-token-1");
            assertEquals(1, connected.size());
            MetaConnectedPage saved = connected.get(0);
            assertEquals("p1", saved.getPageId());
            assertEquals("Page One", saved.getPageName());
            assertEquals("page-token-1", cipher.decrypt(saved.getPageAccessTokenEncrypted()));
            assertFalse(saved.getPageAccessTokenEncrypted().contains("page-token-1"));
            assertEquals(admin, saved.getGrantedBy());
        }

        @Test
        void reconnectingAPageReplacesItsRowAndToken() {
            grantTwoPages();
            MetaConnectedPage existing = new MetaConnectedPage(acme, "p1");
            existing.replace("Old name", cipher.encrypt("old-token"), admin, NOW.minusSeconds(60));
            when(pages.findByOrganizationIdAndPageId(acme.getId(), "p1")).thenReturn(Optional.of(existing));

            List<MetaConnectedPage> connected = service.connect("sub", acme.getId(), List.of("p1"));

            assertSame(existing, connected.get(0));
            assertEquals("page-token-1", cipher.decrypt(existing.getPageAccessTokenEncrypted()));
            assertNotEquals("Old name", existing.getPageName());
            verify(pages).save(existing);
        }

        @Test
        void aPageOutsideTheGrantIsRefusedBeforeAnySubscription() {
            grantTwoPages();

            assertThrows(MetaPageNotAllowedException.class,
                    () -> service.connect("sub", acme.getId(), List.of("p1", "not-granted")));

            verify(meta, never()).subscribePage(anyString(), anyString());
            verify(pages, never()).save(any());
        }

        @Test
        void withoutAStoredGrantNothingIsConnected() {
            when(authorizations.findByOrganizationId(acme.getId())).thenReturn(Optional.empty());

            assertThrows(MetaPageNotAllowedException.class,
                    () -> service.connect("sub", acme.getId(), List.of("p1")));
            verify(meta, never()).listPages(anyString());
        }

        @Test
        void aGraphFailureOnSubscribeSavesNothing() {
            grantTwoPages();
            doThrow(new MetaAuthorizationFailedException("subscribe failed"))
                    .when(meta).subscribePage("p1", "page-token-1");

            assertThrows(MetaAuthorizationFailedException.class,
                    () -> service.connect("sub", acme.getId(), List.of("p1")));
            verify(pages, never()).save(any());
        }

        @Test
        void aNonMemberIsNotFoundAndMetaIsNotCalled() {
            when(tenants.requireRole(acme.getId(), admin.getId(), Roles.ADMIN)).thenThrow(new NotAMemberException());

            assertThrows(NotAMemberException.class, () -> service.connect("sub", acme.getId(), List.of("p1")));
            verify(meta, never()).listPages(anyString());
        }

        @Test
        void anAdminWhoLostTheRoleIsForbidden() {
            when(tenants.requireRole(acme.getId(), admin.getId(), Roles.ADMIN))
                    .thenThrow(new InsufficientOrganizationRoleException());

            assertThrows(InsufficientOrganizationRoleException.class,
                    () -> service.connect("sub", acme.getId(), List.of("p1")));
            verify(meta, never()).subscribePage(anyString(), anyString());
        }

        @Test
        void withoutMetaSettingsItIsNotConfigured() {
            MetaProperties unset = new MetaProperties("", "", null, "", "", KEY);
            MetaPageService unconfigured = new MetaPageService(unset, authorizations, pages, meta, cipher, tenants,
                    callers, CLOCK);

            assertThrows(MetaNotConfiguredException.class,
                    () -> unconfigured.connect("sub", acme.getId(), List.of("p1")));
        }
    }

    @Nested
    class ListPages {

        @Test
        void anAdminGetsThePagesOfTheOrganization() {
            MetaConnectedPage page = new MetaConnectedPage(acme, "p1");
            page.replace("Page One", cipher.encrypt("page-token-1"), admin, NOW);
            Page<MetaConnectedPage> found = new PageImpl<>(List.of(page));
            when(pages.findByOrganizationId(eq(acme.getId()), any(Pageable.class))).thenReturn(found);

            assertSame(found, service.connectedPages("sub", acme.getId(), 0, 20));
        }

        @Test
        void aNonMemberIsNotFound() {
            when(tenants.requireRole(acme.getId(), admin.getId(), Roles.ADMIN)).thenThrow(new NotAMemberException());

            assertThrows(NotAMemberException.class, () -> service.connectedPages("sub", acme.getId(), 0, 20));
        }
    }
}
