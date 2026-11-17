package com.connecta.backend.org.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.connecta.backend.config.JwtProperties;
import com.connecta.backend.org.domain.Membership;
import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.org.repository.MembershipRepository;
import com.connecta.backend.org.repository.OrganizationRepository;
import com.connecta.backend.org.service.NotAMemberException;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Real PostgreSQL and the full security chain: a member is let in, a non-member gets the same 404 as an
 * unknown organization, a lower role gets 403, and no token gets 401. Skipped when Docker is absent.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false"})
@AutoConfigureMockMvc
@Import(TestClock.class)
@ContextConfiguration(initializers = RandomJwtSecret.class)
class TenantExceptionHandlerIntegrationTests {

    private static final String MEMBER_PATH = "/api/test/organizations/%s/member";
    private static final String ADMIN_PATH = "/api/test/organizations/%s/admin";

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

    private User newUser() {
        return users.saveAndFlush(new User("u" + System.nanoTime() + "@example.com", "hash", "Test User"));
    }

    private Organization newOrganization() {
        return organizations.saveAndFlush(new Organization("Org", "org-" + System.nanoTime()));
    }

    private void join(User user, Organization organization, String roleName) {
        Role role = roles.findByName(roleName).orElseThrow();
        memberships.saveAndFlush(new Membership(organization, user, role));
    }

    private String bearer(User user) {
        return TestTokens.bearer(jwtProperties, user);
    }

    @Nested
    class MemberAccess {

        @Test
        void memberIsAllowedOnAPathThatNeedsMembershipOnly() throws Exception {
            User user = newUser();
            Organization org = newOrganization();
            join(user, org, Roles.AGENT);

            mvc.perform(get(MEMBER_PATH.formatted(org.getId())).header("Authorization", bearer(user)))
                    .andExpect(status().isOk());
        }

        @Test
        void higherRoleIsAllowedOnAPathThatNeedsAdmin() throws Exception {
            User user = newUser();
            Organization org = newOrganization();
            join(user, org, Roles.ADMIN);

            mvc.perform(get(ADMIN_PATH.formatted(org.getId())).header("Authorization", bearer(user)))
                    .andExpect(status().isOk());
        }

        @Test
        void nonMemberGets404WithTheSameBodyAsAnUnknownOrganization() throws Exception {
            User user = newUser();
            Organization someoneElses = newOrganization();
            join(newUser(), someoneElses, Roles.ADMIN);

            String otherTenant = mvc.perform(get(MEMBER_PATH.formatted(someoneElses.getId()))
                            .header("Authorization", bearer(user)))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.detail").value(new NotAMemberException().getMessage()))
                    .andReturn().getResponse().getContentAsString();
            UUID unknownId = UUID.randomUUID();
            String unknown = mvc.perform(get(MEMBER_PATH.formatted(unknownId))
                            .header("Authorization", bearer(user)))
                    .andExpect(status().isNotFound())
                    .andReturn().getResponse().getContentAsString();

            // The only difference allowed is the echoed request path (ProblemDetail.instance) that holds the id.
            assertEquals(unknown.replace(unknownId.toString(), "ID"),
                    otherTenant.replace(someoneElses.getId().toString(), "ID"));
        }
    }

    @Nested
    class RoleFailures {

        @Test
        void lowerRoleGets403() throws Exception {
            User user = newUser();
            Organization org = newOrganization();
            join(user, org, Roles.AGENT);

            mvc.perform(get(ADMIN_PATH.formatted(org.getId())).header("Authorization", bearer(user)))
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.detail").exists());
        }

        @Test
        void missingTokenGets401() throws Exception {
            mvc.perform(get(MEMBER_PATH.formatted(UUID.randomUUID())))
                    .andExpect(status().isUnauthorized());
        }
    }
}
