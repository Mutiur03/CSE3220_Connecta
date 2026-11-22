package com.connecta.backend.meta.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.connecta.backend.config.JwtProperties;
import com.connecta.backend.meta.domain.MetaAuthorization;
import com.connecta.backend.meta.domain.MetaConnectedPage;
import com.connecta.backend.meta.repository.MetaAuthorizationRepository;
import com.connecta.backend.meta.repository.MetaConnectedPageRepository;
import com.connecta.backend.meta.service.MetaOAuthClient;
import com.connecta.backend.meta.service.MetaOAuthClient.MetaPage;
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
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The connected-Pages endpoints against real PostgreSQL (Flyway runs the migrations). Meta's API is mocked, so no
 * request leaves the test. Covers 401, the 403 from the URL rule and from the service, the cross-tenant 404, the
 * list without tokens, connecting a granted Page (token stored encrypted), and the 422 for a Page outside the grant.
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
class MetaPageControllerIntegrationTests {

    private static final String AUTH = "Authorization";
    private static final String USER_TOKEN = "user-token";

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
    MetaConnectedPageRepository pages;

    @Autowired
    TokenCipher cipher;

    private static String path(Organization organization) {
        return "/api/organizations/" + organization.getId() + "/meta/pages";
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

    /** An organization with an ADMIN (global ADMIN) and an AGENT (global AGENT), and a stored Meta grant. */
    private record Seeded(Organization acme, User admin, User agent) {
    }

    private Seeded seedAcmeWithGrant() {
        Organization acme = organization("Acme");
        User admin = newUser(Roles.ADMIN);
        User agent = newUser(Roles.AGENT);
        join(acme, admin, Roles.ADMIN);
        join(acme, agent, Roles.AGENT);
        MetaAuthorization grant = new MetaAuthorization(acme);
        grant.replaceToken(cipher.encrypt(USER_TOKEN), admin, null, TestClock.NOW);
        authorizations.saveAndFlush(grant);
        return new Seeded(acme, admin, agent);
    }

    private void grantOnePage() {
        when(meta.listPages(USER_TOKEN)).thenReturn(List.of(new MetaPage("p1", "Page One", "page-token-1")));
    }

    @Nested
    class Connect {

        @Test
        void anAdminConnectsAGrantedPageAndItsTokenIsStoredEncrypted() throws Exception {
            Seeded s = seedAcmeWithGrant();
            grantOnePage();

            mvc.perform(post(path(s.acme())).header(AUTH, bearer(s.admin()))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"pageIds\":[\"p1\"]}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].pageId").value("p1"))
                    .andExpect(jsonPath("$[0].name").value("Page One"));

            List<MetaConnectedPage> stored = pages.findAll().stream()
                    .filter(page -> page.getOrganization().getId().equals(s.acme().getId()))
                    .toList();
            assertEquals(1, stored.size());
            assertFalse(stored.get(0).getPageAccessTokenEncrypted().contains("page-token-1"));
            assertEquals("page-token-1", cipher.decrypt(stored.get(0).getPageAccessTokenEncrypted()));
        }

        @Test
        void aPageOutsideTheGrantIsUnprocessable() throws Exception {
            Seeded s = seedAcmeWithGrant();
            grantOnePage();

            mvc.perform(post(path(s.acme())).header(AUTH, bearer(s.admin()))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"pageIds\":[\"not-granted\"]}"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.detail")
                            .value("One or more pages are not part of the Meta authorization"));
        }

        @Test
        void anEmptyPageListIsBadRequest() throws Exception {
            Seeded s = seedAcmeWithGrant();

            mvc.perform(post(path(s.acme())).header(AUTH, bearer(s.admin()))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"pageIds\":[]}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void anAgentIsForbidden() throws Exception {
            Seeded s = seedAcmeWithGrant();

            mvc.perform(post(path(s.acme())).header(AUTH, bearer(s.agent()))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"pageIds\":[\"p1\"]}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        void aGlobalAdminWhoIsOnlyAnAgentOfTheOrganizationIsForbidden() throws Exception {
            Organization acme = organization("Acme");
            User globalAdmin = newUser(Roles.ADMIN);
            join(acme, globalAdmin, Roles.AGENT);

            mvc.perform(post(path(acme)).header(AUTH, bearer(globalAdmin))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"pageIds\":[\"p1\"]}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        void aNonMemberGetsNotFound() throws Exception {
            Seeded s = seedAcmeWithGrant();
            User stranger = newUser(Roles.ADMIN);

            mvc.perform(post(path(s.acme())).header(AUTH, bearer(stranger))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"pageIds\":[\"p1\"]}"))
                    .andExpect(status().isNotFound());
        }

        @Test
        void anUnauthenticatedCallerGetsUnauthorized() throws Exception {
            Seeded s = seedAcmeWithGrant();

            mvc.perform(post(path(s.acme())).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"pageIds\":[\"p1\"]}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class ListPages {

        @Test
        void anAdminListsConnectedPagesWithoutTokens() throws Exception {
            Seeded s = seedAcmeWithGrant();
            MetaConnectedPage page = new MetaConnectedPage(s.acme(), "p1");
            page.replace("Page One", cipher.encrypt("page-token-1"), s.admin(), TestClock.NOW);
            pages.saveAndFlush(page);

            mvc.perform(get(path(s.acme())).header(AUTH, bearer(s.admin())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].pageId").value("p1"))
                    .andExpect(jsonPath("$.content[0].name").value("Page One"))
                    .andExpect(jsonPath("$.content[0].pageAccessTokenEncrypted").doesNotExist())
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        void anAgentIsForbidden() throws Exception {
            Seeded s = seedAcmeWithGrant();

            mvc.perform(get(path(s.acme())).header(AUTH, bearer(s.agent())))
                    .andExpect(status().isForbidden());
        }

        @Test
        void anotherOrganizationsPagesAreNotFound() throws Exception {
            Seeded s = seedAcmeWithGrant();
            Organization other = organization("Other");
            join(other, newUser(Roles.ADMIN), Roles.ADMIN);

            mvc.perform(get(path(other)).header(AUTH, bearer(s.admin())))
                    .andExpect(status().isNotFound());
        }

        @Test
        void anUnauthenticatedCallerGetsUnauthorized() throws Exception {
            Seeded s = seedAcmeWithGrant();

            mvc.perform(get(path(s.acme()))).andExpect(status().isUnauthorized());
        }
    }
}
