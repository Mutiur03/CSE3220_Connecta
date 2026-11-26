package com.connecta.backend.org.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.connecta.backend.config.JwtProperties;
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
 * The availability endpoints against real PostgreSQL (Flyway runs the migrations). Each endpoint has an allowed
 * role and a denied role, plus 401, 400 and the cross-tenant 404. Skipped
 * without Docker.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false"})
@AutoConfigureMockMvc
@Import(TestClock.class)
@ContextConfiguration(initializers = RandomJwtSecret.class)
class AvailabilityControllerIntegrationTests {

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

    private static String path(Organization organization, String suffix) {
        return "/api/organizations/" + organization.getId() + "/availability" + suffix;
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
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
        return organizations.saveAndFlush(new Organization(name, "org-" + UUID.randomUUID()));
    }

    private Membership join(Organization organization, User user, String roleName) {
        return memberships.saveAndFlush(new Membership(organization, user, role(roleName)));
    }

    private String bearer(User user) {
        return TestTokens.bearer(jwtProperties, user);
    }

    private static String statusBody(String status) {
        return "{\"status\":\"" + status + "\"}";
    }

    @Nested
    class SetOwn {

        @Test
        void anAgentSetsTheirOwnStatusAndItIsKept() throws Exception {
            User agent = newUser();
            Organization acme = organization("Acme");
            join(acme, agent, Roles.AGENT);

            mvc.perform(json(put(path(acme, "/me")), statusBody("AVAILABLE")).header(AUTH, bearer(agent)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("AVAILABLE"))
                    .andExpect(jsonPath("$.userId").value(agent.getId().toString()))
                    .andExpect(jsonPath("$.updatedAt").exists());
            mvc.perform(json(put(path(acme, "/me")), statusBody("AWAY")).header(AUTH, bearer(agent)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("AWAY"));
            mvc.perform(get(path(acme, "/me")).header(AUTH, bearer(agent)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("AWAY"));
        }

        @Test
        void anUnknownStatusIsRejected() throws Exception {
            User agent = newUser();
            Organization acme = organization("Acme");
            join(acme, agent, Roles.AGENT);

            mvc.perform(json(put(path(acme, "/me")), statusBody("BUSY")).header(AUTH, bearer(agent)))
                    .andExpect(status().isBadRequest());
            mvc.perform(json(put(path(acme, "/me")), "{}").header(AUTH, bearer(agent)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void aNonMemberGetsA404() throws Exception {
            User outsider = newUser();
            join(organization("Mine"), outsider, Roles.ADMIN);
            Organization theirs = organization("Theirs");

            mvc.perform(json(put(path(theirs, "/me")), statusBody("AVAILABLE")).header(AUTH, bearer(outsider)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void anAnonymousCallerIsUnauthorized() throws Exception {
            mvc.perform(json(put(path(organization("Acme"), "/me")), statusBody("AVAILABLE")))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class GetOwn {

        @Test
        void aMemberWhoNeverSetItIsAway() throws Exception {
            User agent = newUser();
            Organization acme = organization("Acme");
            join(acme, agent, Roles.AGENT);

            mvc.perform(get(path(acme, "/me")).header(AUTH, bearer(agent)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("AWAY"))
                    .andExpect(jsonPath("$.role").value("AGENT"));
        }

        @Test
        void aNonMemberGetsA404() throws Exception {
            User outsider = newUser();
            join(organization("Mine"), outsider, Roles.ADMIN);

            mvc.perform(get(path(organization("Theirs"), "/me")).header(AUTH, bearer(outsider)))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class ListAll {

        @Test
        void anAdminSeesEveryMemberAndMissingRowsAreAway() throws Exception {
            User admin = newUser();
            User agent = newUser();
            Organization acme = organization("Acme");
            join(acme, admin, Roles.ADMIN);
            join(acme, agent, Roles.AGENT);
            mvc.perform(json(put(path(acme, "/me")), statusBody("AVAILABLE")).header(AUTH, bearer(agent)))
                    .andExpect(status().isOk());

            mvc.perform(get(path(acme, "")).header(AUTH, bearer(admin)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(2))
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.size").value(20))
                    .andExpect(jsonPath("$.content[?(@.userId=='" + agent.getId() + "')].status").value("AVAILABLE"))
                    .andExpect(jsonPath("$.content[?(@.userId=='" + admin.getId() + "')].status").value("AWAY"));
        }

        @Test
        void anAgentIsForbidden() throws Exception {
            User agent = newUser();
            Organization acme = organization("Acme");
            join(acme, agent, Roles.AGENT);

            mvc.perform(get(path(acme, "")).header(AUTH, bearer(agent)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void anAdminOfAnotherOrganizationGetsA404() throws Exception {
            User outsider = newUser();
            join(organization("Mine"), outsider, Roles.ADMIN);
            Organization theirs = organization("Theirs");
            join(theirs, newUser(), Roles.ADMIN);

            mvc.perform(get(path(theirs, "")).header(AUTH, bearer(outsider)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void sizeAboveTheMaximumIsRejected() throws Exception {
            User admin = newUser();
            Organization acme = organization("Acme");
            join(acme, admin, Roles.ADMIN);

            mvc.perform(get(path(acme, "")).param("size", "101").header(AUTH, bearer(admin)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void anAnonymousCallerIsUnauthorized() throws Exception {
            mvc.perform(get(path(organization("Acme"), ""))).andExpect(status().isUnauthorized());
        }
    }
}
