package com.connecta.backend.org.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.connecta.backend.config.JwtProperties;
import com.connecta.backend.org.domain.Membership;
import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.org.repository.MembershipRepository;
import com.connecta.backend.support.TestUsers;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The organization endpoints against real PostgreSQL (Flyway runs the migrations). Each endpoint has
 * an allowed role and a denied role, plus the 401 and cross-tenant 404 cases. Skipped without Docker.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false"})
@AutoConfigureMockMvc
@Import(TestClock.class)
@ContextConfiguration(initializers = RandomJwtSecret.class)
class OrganizationControllerIntegrationTests {

    private static final String BASE = "/api/organizations";
    private static final String AUTH = "Authorization";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

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

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static String createBody(String name, String slug) {
        return "{\"name\":\"" + name + "\",\"slug\":\"" + slug + "\"}";
    }

    private static String newSlug() {
        return "org-" + UUID.randomUUID();
    }

    private User newUser() {
        User user = new User("u" + UUID.randomUUID() + "@example.com", "hash", "Test User");
        user.addRole(role(Roles.AGENT));
        return users.saveAndFlush(user);
    }

    private Role role(String name) {
        return roles.findByName(name).orElseThrow();
    }

    private Organization organization(String name) {
        return organizations.saveAndFlush(new Organization(name, newSlug()));
    }

    private void join(Organization organization, User user, String roleName) {
        memberships.saveAndFlush(new Membership(organization, user, role(roleName)));
    }

    private String bearer(User user) {
        return TestTokens.bearer(jwtProperties, user);
    }

    private String roleOf(Organization organization, User user) {
        return memberships.findByOrganizationIdAndUserId(organization.getId(), user.getId())
                .orElseThrow().getRole().getName();
    }

    @Nested
    class Create {

        @Test
        void theCallerBecomesOwnerAndGetsCreatedWithALocation() throws Exception {
            User caller = newUser();
            String slug = newSlug();

            mvc.perform(json(post(BASE).header(AUTH, bearer(caller)), createBody("Acme", slug)))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", containsString(BASE + "/")))
                    .andExpect(jsonPath("$.name").value("Acme"))
                    .andExpect(jsonPath("$.slug").value(slug));

            Organization created = organizations.findBySlug(slug).orElseThrow();
            assertEquals(Roles.OWNER, roleOf(created, caller));
        }

        @Test
        void aTakenSlugReturns409() throws Exception {
            User caller = newUser();
            String slug = newSlug();
            mvc.perform(json(post(BASE).header(AUTH, bearer(caller)), createBody("First", slug)))
                    .andExpect(status().isCreated());

            mvc.perform(json(post(BASE).header(AUTH, bearer(caller)), createBody("Second", slug)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409));
        }

        @Test
        void invalidInputReturns400WithTheBadFields() throws Exception {
            User caller = newUser();

            mvc.perform(json(post(BASE).header(AUTH, bearer(caller)), createBody("  ", "Not Kebab")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.name").exists())
                    .andExpect(jsonPath("$.errors.slug").exists());
        }

        @Test
        void anonymousCallerIsUnauthorized() throws Exception {
            mvc.perform(json(post(BASE), createBody("Acme", newSlug())))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class ListMine {

        @Test
        void returnsOnlyMyOrganizationsInTheRequestedPage() throws Exception {
            User caller = newUser();
            organization("Alpha");
            join(organization("Bravo"), caller, Roles.OWNER);
            join(organization("Charlie"), caller, Roles.AGENT);
            join(organization("Delta"), caller, Roles.ADMIN);
            join(organization("Other Tenant"), newUser(), Roles.OWNER);

            mvc.perform(get(BASE).header(AUTH, bearer(caller)).param("page", "0").param("size", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.content[0].name").value("Bravo"))
                    .andExpect(jsonPath("$.content[0].callerRole").value("OWNER"))
                    .andExpect(jsonPath("$.content[1].name").value("Charlie"))
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.size").value(2))
                    .andExpect(jsonPath("$.totalElements").value(3));
        }

        @Test
        void theLastPageHoldsTheRest() throws Exception {
            User caller = newUser();
            join(organization("Bravo"), caller, Roles.OWNER);
            join(organization("Charlie"), caller, Roles.AGENT);
            join(organization("Delta"), caller, Roles.ADMIN);

            mvc.perform(get(BASE).header(AUTH, bearer(caller)).param("page", "1").param("size", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].name").value("Delta"));
        }

        @Test
        void sizeAboveTheMaximumIsRejected() throws Exception {
            mvc.perform(get(BASE).header(AUTH, bearer(newUser())).param("size", "101"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void aNegativePageIsRejected() throws Exception {
            mvc.perform(get(BASE).header(AUTH, bearer(newUser())).param("page", "-1"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void anonymousCallerIsUnauthorized() throws Exception {
            mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class Get {

        @Test
        void anAgentMemberCanReadTheOrganization() throws Exception {
            User agent = newUser();
            Organization acme = organization("Acme");
            join(acme, agent, Roles.AGENT);

            mvc.perform(get(BASE + "/" + acme.getId()).header(AUTH, bearer(agent)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(acme.getId().toString()))
                    .andExpect(jsonPath("$.callerRole").value("AGENT"))
                    .andExpect(jsonPath("$.name").value("Acme"));
        }

        @Test
        void aNonMemberGetsTheSame404AsAnUnknownId() throws Exception {
            Organization acme = organization("Acme");

            mvc.perform(get(BASE + "/" + acme.getId()).header(AUTH, bearer(newUser())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404));
            mvc.perform(get(BASE + "/" + UUID.randomUUID()).header(AUTH, bearer(newUser())))
                    .andExpect(status().isNotFound());
        }

        @Test
        void anonymousCallerIsUnauthorized() throws Exception {
            mvc.perform(get(BASE + "/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class Rename {

        @Test
        void anOwnerRenamesAndTheSlugStaysTheSame() throws Exception {
            User owner = newUser();
            Organization acme = organization("Acme");
            join(acme, owner, Roles.OWNER);

            mvc.perform(json(patch(BASE + "/" + acme.getId()).header(AUTH, bearer(owner)),
                            "{\"name\":\"Acme Desk\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Acme Desk"));

            Organization renamed = organizations.findById(acme.getId()).orElseThrow();
            assertEquals("Acme Desk", renamed.getName());
            assertEquals(acme.getSlug(), renamed.getSlug());
        }

        @Test
        void anAdminCanRename() throws Exception {
            User admin = newUser();
            Organization acme = organization("Acme");
            join(acme, admin, Roles.ADMIN);

            mvc.perform(json(patch(BASE + "/" + acme.getId()).header(AUTH, bearer(admin)),
                            "{\"name\":\"Acme Desk\"}"))
                    .andExpect(status().isOk());
        }

        @Test
        void anAgentIsForbiddenAndTheNameIsKept() throws Exception {
            User agent = newUser();
            Organization acme = organization("Acme");
            join(acme, agent, Roles.AGENT);

            mvc.perform(json(patch(BASE + "/" + acme.getId()).header(AUTH, bearer(agent)),
                            "{\"name\":\"Hacked\"}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403));
            assertEquals("Acme", organizations.findById(acme.getId()).orElseThrow().getName());
        }

        @Test
        void aNonMemberGetsA404() throws Exception {
            Organization acme = organization("Acme");

            mvc.perform(json(patch(BASE + "/" + acme.getId()).header(AUTH, bearer(newUser())),
                            "{\"name\":\"Hacked\"}"))
                    .andExpect(status().isNotFound());
        }

        @Test
        void aBlankNameIsRejected() throws Exception {
            User owner = newUser();
            Organization acme = organization("Acme");
            join(acme, owner, Roles.OWNER);

            mvc.perform(json(patch(BASE + "/" + acme.getId()).header(AUTH, bearer(owner)), "{\"name\":\" \"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.name").exists());
        }

        @Test
        void anonymousCallerIsUnauthorized() throws Exception {
            mvc.perform(json(patch(BASE + "/" + UUID.randomUUID()), "{\"name\":\"Acme\"}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class DisabledAccount {

        @Test
        void aDisabledUserWithAValidTokenIsRefusedOnEveryEndpoint() throws Exception {
            User user = newUser();
            Organization acme = organization("Acme");
            join(acme, user, Roles.OWNER);
            TestUsers.set(user, "enabled", false);
            users.saveAndFlush(user);
            String token = bearer(user);

            mvc.perform(get(BASE).header(AUTH, token)).andExpect(status().isUnauthorized());
            mvc.perform(get(BASE + "/" + acme.getId()).header(AUTH, token)).andExpect(status().isUnauthorized());
            mvc.perform(json(patch(BASE + "/" + acme.getId()).header(AUTH, token), "{\"name\":\"Acme Desk\"}"))
                    .andExpect(status().isUnauthorized());
            mvc.perform(delete(BASE + "/" + acme.getId()).header(AUTH, token)).andExpect(status().isUnauthorized());

            assertEquals("Acme", organizations.findById(acme.getId()).orElseThrow().getName());
            assertTrue(organizations.existsById(acme.getId()));
        }
    }

    @Nested
    class Delete {

        @Test
        void anOwnerDeletesTheOrganizationAndItsMemberships() throws Exception {
            User owner = newUser();
            Organization acme = organization("Acme");
            join(acme, owner, Roles.OWNER);

            mvc.perform(delete(BASE + "/" + acme.getId()).header(AUTH, bearer(owner)))
                    .andExpect(status().isNoContent());

            assertFalse(organizations.existsById(acme.getId()));
            assertTrue(memberships.findByOrganizationIdAndUserId(acme.getId(), owner.getId()).isEmpty());
        }

        @Test
        void anAdminIsForbiddenAndTheOrganizationStays() throws Exception {
            User admin = newUser();
            Organization acme = organization("Acme");
            join(acme, admin, Roles.ADMIN);

            mvc.perform(delete(BASE + "/" + acme.getId()).header(AUTH, bearer(admin)))
                    .andExpect(status().isForbidden());
            assertTrue(organizations.existsById(acme.getId()));
        }

        @Test
        void anAgentIsForbiddenAndTheOrganizationStays() throws Exception {
            User agent = newUser();
            Organization acme = organization("Acme");
            join(acme, agent, Roles.AGENT);

            mvc.perform(delete(BASE + "/" + acme.getId()).header(AUTH, bearer(agent)))
                    .andExpect(status().isForbidden());
            assertTrue(organizations.existsById(acme.getId()));
        }

        @Test
        void aNonMemberGetsA404AndTheOrganizationStays() throws Exception {
            Organization acme = organization("Acme");

            mvc.perform(delete(BASE + "/" + acme.getId()).header(AUTH, bearer(newUser())))
                    .andExpect(status().isNotFound());
            assertTrue(organizations.existsById(acme.getId()));
        }

        @Test
        void anonymousCallerIsUnauthorized() throws Exception {
            mvc.perform(delete(BASE + "/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
        }
    }
}
