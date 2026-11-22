package com.connecta.backend.meta.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.connecta.backend.config.JwtProperties;
import com.connecta.backend.meta.domain.MetaAuthorization;
import com.connecta.backend.meta.repository.MetaAuthorizationRepository;
import com.connecta.backend.meta.service.MetaAuthorizationFailedException;
import com.connecta.backend.meta.service.MetaAuthorizationService;
import com.connecta.backend.meta.service.MetaOAuthClient;
import com.connecta.backend.meta.service.MetaOAuthClient.MetaUserToken;
import com.connecta.backend.meta.service.OAuthStateSigner;
import com.connecta.backend.meta.service.TokenCipher;
import com.connecta.backend.org.domain.Membership;
import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.org.repository.MembershipRepository;
import com.connecta.backend.org.repository.OrganizationRepository;
import com.connecta.backend.support.RandomJwtSecret;
import com.connecta.backend.support.TestClock;
import com.connecta.backend.support.TestTokens;
import com.connecta.backend.user.domain.Role;
import com.connecta.backend.user.domain.Roles;
import com.connecta.backend.user.domain.User;
import com.connecta.backend.user.repository.RoleRepository;
import com.connecta.backend.user.repository.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The Meta connection endpoints against real PostgreSQL (Flyway runs the migrations). Meta's own API is replaced
 * by a mock, so no request leaves the test. Covers the role rules (401, 403, cross-tenant 404), the stored token
 * being encrypted, and the callback's state checks. Skipped without Docker.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false",
        // A test-only key, set here so this test does not depend on the shared test properties.
        "app.meta.token-encryption-key=dGVzdC1vbmx5LW1ldGEtdG9rZW4ta2V5LTAwMDAwMDE="})
@AutoConfigureMockMvc
@Import(TestClock.class)
@ContextConfiguration(initializers = RandomJwtSecret.class)
class MetaAuthorizationControllerIntegrationTests {

    private static final String AUTH = "Authorization";
    private static final String DIALOG_URL = "https://www.facebook.com/v27.0/dialog/oauth?state=x";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @MockitoBean
    MetaOAuthClient meta;

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtProperties jwtProperties;

    @Autowired
    UserRepository users;

    @Autowired
    RoleRepository roles;

    @Autowired
    OrganizationRepository organizations;

    @Autowired
    MembershipRepository memberships;

    @Autowired
    MetaAuthorizationRepository authorizations;

    @Autowired
    OAuthStateSigner states;

    @Autowired
    TokenCipher cipher;

    private static String path(Organization organization) {
        return "/api/organizations/" + organization.getId() + "/meta/authorization";
    }

    private User newUser(String globalRole) {
        User user = new User("u" + UUID.randomUUID() + "@example.com", "hash", "Test User");
        user.addRole(roles.findByName(globalRole).orElseThrow());
        return users.saveAndFlush(user);
    }

    private Organization organization(String name) {
        return organizations.saveAndFlush(new Organization(name, "org-" + UUID.randomUUID()));
    }

    private void join(Organization organization, User user, String roleName) {
        Role role = roles.findByName(roleName).orElseThrow();
        memberships.saveAndFlush(new Membership(organization, user, role));
    }

    private String bearer(User user) {
        return TestTokens.bearer(jwtProperties, user);
    }

    /** An organization with an ADMIN member (global ADMIN) and an AGENT member (global AGENT). */
    private record Seeded(Organization acme, User admin, User agent) {
    }

    private Seeded seedAcme() {
        Organization acme = organization("Acme");
        User admin = newUser(Roles.ADMIN);
        User agent = newUser(Roles.AGENT);
        join(acme, admin, Roles.ADMIN);
        join(acme, agent, Roles.AGENT);
        return new Seeded(acme, admin, agent);
    }

    @Nested
    class Start {

        @Test
        void anAdminGetsTheMetaLoginUrl() throws Exception {
            Seeded s = seedAcme();
            when(meta.authorizationUrl(anyString(), eq("http://localhost:8080"
                    + MetaAuthorizationService.CALLBACK_PATH))).thenReturn(DIALOG_URL);

            mvc.perform(post(path(s.acme())).header(AUTH, bearer(s.admin())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.authorizationUrl").value(DIALOG_URL));
        }

        @Test
        void anAgentIsForbidden() throws Exception {
            Seeded s = seedAcme();

            mvc.perform(post(path(s.acme())).header(AUTH, bearer(s.agent())))
                    .andExpect(status().isForbidden());
        }

        @Test
        void aGlobalAdminWhoIsOnlyAnAgentOfTheOrganizationIsForbidden() throws Exception {
            Organization acme = organization("Acme");
            User globalAdmin = newUser(Roles.ADMIN);
            join(acme, globalAdmin, Roles.AGENT);

            mvc.perform(post(path(acme)).header(AUTH, bearer(globalAdmin)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void aNonMemberGetsNotFound() throws Exception {
            Seeded s = seedAcme();
            User stranger = newUser(Roles.ADMIN);

            mvc.perform(post(path(s.acme())).header(AUTH, bearer(stranger)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void anAdminOfAnotherOrganizationGetsNotFound() throws Exception {
            Seeded s = seedAcme();
            Organization other = organization("Other");
            join(other, newUser(Roles.ADMIN), Roles.ADMIN);

            mvc.perform(post(path(other)).header(AUTH, bearer(s.admin())))
                    .andExpect(status().isNotFound());
        }

        @Test
        void anUnauthenticatedCallerGetsUnauthorized() throws Exception {
            Seeded s = seedAcme();

            mvc.perform(post(path(s.acme()))).andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class Status {

        @Test
        void anOrganizationThatNeverConnectedReportsNotConnected() throws Exception {
            Seeded s = seedAcme();

            mvc.perform(get(path(s.acme())).header(AUTH, bearer(s.admin())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.connected").value(false));
        }

        @Test
        void anAgentIsForbidden() throws Exception {
            Seeded s = seedAcme();

            mvc.perform(get(path(s.acme())).header(AUTH, bearer(s.agent())))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    class Callback {

        private static final String CALLBACK = MetaAuthorizationService.CALLBACK_PATH;
        private static final String FRONTEND = "http://localhost:3000";

        private String integrations(Seeded s, String query) {
            return FRONTEND + "/organizations/" + s.acme().getId() + "/integrations?" + query;
        }

        @Test
        void aValidCallbackStoresTheTokenEncryptedAndSendsTheAdminBackToIntegrations() throws Exception {
            Seeded s = seedAcme();
            String state = states.issue(s.acme().getId(), s.admin().getId(), TestClock.NOW);
            Instant expires = TestClock.NOW.plusSeconds(5_184_000);
            when(meta.exchangeCode(anyString(), anyString())).thenReturn(new MetaUserToken("short", TestClock.NOW));
            when(meta.extendToken("short")).thenReturn(new MetaUserToken("long-token", expires));

            mvc.perform(get(CALLBACK).param("code", "the-code").param("state", state))
                    .andExpect(status().isFound())
                    .andExpect(header().string("Location", integrations(s, "facebook=connected")));

            MetaAuthorization grant = authorizations.findByOrganizationId(s.acme().getId()).orElseThrow();
            assertFalse(grant.getAccessTokenEncrypted().contains("long-token"));
            assertEquals("long-token", cipher.decrypt(grant.getAccessTokenEncrypted()));
            assertEquals(s.admin().getId(), grant.getGrantedBy().getId());
        }

        @Test
        void aTamperedStateGoesBackToTheOrganizationsPage() throws Exception {
            mvc.perform(get(CALLBACK).param("code", "the-code").param("state", "forged.state"))
                    .andExpect(status().isFound())
                    .andExpect(header().string("Location", FRONTEND + "/organizations?metaError=invalid_state"));
        }

        @Test
        void aCancelledLoginGoesBackWithDenied() throws Exception {
            Seeded s = seedAcme();
            String state = states.issue(s.acme().getId(), s.admin().getId(), TestClock.NOW);

            mvc.perform(get(CALLBACK).param("state", state))
                    .andExpect(status().isFound())
                    .andExpect(header().string("Location", integrations(s, "metaError=denied")));
            assertTrue(authorizations.findByOrganizationId(s.acme().getId()).isEmpty());
        }

        @Test
        void aMetaRefusalGoesBackWithAGenericCodeAndSavesNothing() throws Exception {
            Seeded s = seedAcme();
            String state = states.issue(s.acme().getId(), s.admin().getId(), TestClock.NOW);
            when(meta.exchangeCode(anyString(), anyString()))
                    .thenThrow(new MetaAuthorizationFailedException("Invalid verification code"));

            mvc.perform(get(CALLBACK).param("code", "bad").param("state", state))
                    .andExpect(status().isFound())
                    .andExpect(header().string("Location", integrations(s, "metaError=meta_failed")));
            assertTrue(authorizations.findByOrganizationId(s.acme().getId()).isEmpty());
        }

        @Test
        void anAdminWhoLostTheRoleGoesBackWithForbidden() throws Exception {
            Seeded s = seedAcme();
            String state = states.issue(s.acme().getId(), s.agent().getId(), TestClock.NOW);

            mvc.perform(get(CALLBACK).param("code", "the-code").param("state", state))
                    .andExpect(status().isFound())
                    .andExpect(header().string("Location", integrations(s, "metaError=forbidden")));
        }
    }
}
