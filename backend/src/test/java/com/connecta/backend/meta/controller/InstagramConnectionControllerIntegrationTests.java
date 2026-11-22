package com.connecta.backend.meta.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.connecta.backend.config.JwtProperties;
import com.connecta.backend.meta.domain.MetaConnectedAccount;
import com.connecta.backend.meta.repository.MetaConnectedAccountRepository;
import com.connecta.backend.meta.service.InstagramConnectService;
import com.connecta.backend.meta.service.InstagramOAuthClient;
import com.connecta.backend.meta.service.InstagramOAuthClient.InstagramAccount;
import com.connecta.backend.meta.service.MetaAuthorizationFailedException;
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
 * The Instagram connection endpoints against real PostgreSQL (Flyway runs the migrations). Instagram's API is
 * mocked, so no request leaves the test. Covers the role rules (401, 403, cross-tenant 404), the account list
 * without tokens, the stored token being encrypted, and the callback's state and Meta-refusal failures.
 * Skipped without Docker.
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
class InstagramConnectionControllerIntegrationTests {

    private static final String AUTH = "Authorization";
    private static final String LOGIN_URL = "https://www.instagram.com/oauth/authorize?state=x";
    private static final String CALLBACK = InstagramConnectService.CALLBACK_PATH;

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @MockitoBean
    InstagramOAuthClient instagram;

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
    MetaConnectedAccountRepository accounts;

    @Autowired
    OAuthStateSigner states;

    @Autowired
    TokenCipher cipher;

    private static String path(Organization organization) {
        return "/api/organizations/" + organization.getId() + "/meta/instagram";
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

    /** An organization with an ADMIN (global ADMIN) and an AGENT (global AGENT), plus one connected account. */
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

    private void connectAccount(Organization organization, User by, String externalId, String token) {
        MetaConnectedAccount account = new MetaConnectedAccount(organization, MetaConnectedAccount.PLATFORM_INSTAGRAM,
                externalId);
        account.refresh("acme_shop", cipher.encrypt(token), by, TestClock.NOW);
        accounts.saveAndFlush(account);
    }

    @Nested
    class Start {

        @Test
        void anAdminGetsTheInstagramLoginUrl() throws Exception {
            Seeded s = seedAcme();
            when(instagram.authorizationUrl(anyString(), anyString())).thenReturn(LOGIN_URL);

            mvc.perform(post(path(s.acme())).header(AUTH, bearer(s.admin())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.authorizationUrl").value(LOGIN_URL));
        }

        @Test
        void anAgentIsForbidden() throws Exception {
            Seeded s = seedAcme();

            mvc.perform(post(path(s.acme())).header(AUTH, bearer(s.agent())))
                    .andExpect(status().isForbidden());
        }

        @Test
        void aNonMemberGetsNotFound() throws Exception {
            Seeded s = seedAcme();

            mvc.perform(post(path(s.acme())).header(AUTH, bearer(newUser(Roles.ADMIN))))
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
    class Accounts {

        @Test
        void anAdminSeesTheConnectedAccountsWithoutTokens() throws Exception {
            Seeded s = seedAcme();
            connectAccount(s.acme(), s.admin(), "17841", "ig-secret-token");

            String body = mvc.perform(get(path(s.acme()) + "?page=0&size=20").header(AUTH, bearer(s.admin())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].externalAccountId").value("17841"))
                    .andExpect(jsonPath("$.content[0].username").value("acme_shop"))
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andReturn().getResponse().getContentAsString();

            assertFalse(body.contains("ig-secret-token"));
            assertFalse(body.contains("accessToken"));
        }

        @Test
        void anAgentIsForbidden() throws Exception {
            Seeded s = seedAcme();

            mvc.perform(get(path(s.acme())).header(AUTH, bearer(s.agent())))
                    .andExpect(status().isForbidden());
        }

        @Test
        void anotherOrganizationsAccountsAreNotFound() throws Exception {
            Seeded s = seedAcme();
            Organization other = organization("Other");
            join(other, s.admin(), Roles.ADMIN);

            mvc.perform(get(path(other)).header(AUTH, bearer(newUser(Roles.ADMIN))))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class Callback {

        private static final String FRONTEND = "http://localhost:3000";

        private String integrations(Seeded s, String query) {
            return FRONTEND + "/organizations/" + s.acme().getId() + "/integrations?" + query;
        }

        @Test
        void aValidCallbackConnectsTheAccountAndSendsTheAdminBackToIntegrations() throws Exception {
            Seeded s = seedAcme();
            String state = states.issue(s.acme().getId(), s.admin().getId(), TestClock.NOW);
            when(instagram.exchangeCode(anyString(), anyString())).thenReturn(new MetaUserToken("short", TestClock.NOW));
            when(instagram.extendToken("short")).thenReturn(new MetaUserToken("long-token", null));
            when(instagram.fetchAccount("long-token")).thenReturn(new InstagramAccount("17841", "acme_shop"));

            mvc.perform(get(CALLBACK).param("code", "the-code").param("state", state))
                    .andExpect(status().isFound())
                    .andExpect(header().string("Location", integrations(s, "instagram=connected")));

            MetaConnectedAccount stored = accounts.findByOrganizationIdAndPlatformAndExternalAccountId(
                    s.acme().getId(), MetaConnectedAccount.PLATFORM_INSTAGRAM, "17841").orElseThrow();
            assertFalse(stored.getAccessTokenEncrypted().contains("long-token"));
            assertEquals("long-token", cipher.decrypt(stored.getAccessTokenEncrypted()));
            assertEquals(s.admin().getId(), stored.getGrantedBy().getId());
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
        }

        @Test
        void anInstagramRefusalGoesBackWithAGenericCodeAndSavesNothing() throws Exception {
            Seeded s = seedAcme();
            String state = states.issue(s.acme().getId(), s.admin().getId(), TestClock.NOW);
            when(instagram.exchangeCode(anyString(), anyString()))
                    .thenThrow(new MetaAuthorizationFailedException("Instagram request failed"));

            mvc.perform(get(CALLBACK).param("code", "bad").param("state", state))
                    .andExpect(status().isFound())
                    .andExpect(header().string("Location", integrations(s, "metaError=meta_failed")));
            assertFalse(accounts.findByOrganizationIdAndPlatform(s.acme().getId(),
                    MetaConnectedAccount.PLATFORM_INSTAGRAM, org.springframework.data.domain.Pageable.unpaged())
                    .hasContent());
        }
    }
}
