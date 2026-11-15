package com.connecta.backend.org.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
 * The member endpoints against real PostgreSQL (Flyway runs the migrations). Each endpoint has an allowed
 * role and a denied role, plus 401, the cross-tenant 404 and the ceiling and last-owner rules. Skipped
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
class MemberControllerIntegrationTests {

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

    private static String membersPath(Organization organization) {
        return "/api/organizations/" + organization.getId() + "/members";
    }

    private static String memberPath(Organization organization, UUID membershipId) {
        return membersPath(organization) + "/" + membershipId;
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static String roleBody(String role) {
        return "{\"role\":\"" + role + "\"}";
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

    private UUID membershipId(Organization organization, User user) {
        return memberships.findByOrganizationIdAndUserId(organization.getId(), user.getId()).orElseThrow().getId();
    }

    private String roleOf(Organization organization, User user) {
        return memberships.findByOrganizationIdAndUserId(organization.getId(), user.getId())
                .orElseThrow().getRole().getName();
    }

    private String bearer(User user) {
        return TestTokens.bearer(jwtProperties, user);
    }

    @Nested
    class ListMembers {

        @Test
        void aMemberSeesEveryMemberWithoutPasswordData() throws Exception {
            User agent = newUser();
            Organization acme = organization("Acme");
            join(acme, agent, Roles.AGENT);
            join(acme, newUser(), Roles.ADMIN);

            mvc.perform(get(membersPath(acme)).header(AUTH, bearer(agent)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(2))
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.size").value(20))
                    .andExpect(jsonPath("$.content[0].role").exists())
                    .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist())
                    .andExpect(jsonPath("$.content[0].password").doesNotExist());
        }

        @Test
        void pagesAreBoundedAndCountEverything() throws Exception {
            User agent = newUser();
            Organization acme = organization("Acme");
            join(acme, agent, Roles.AGENT);
            join(acme, newUser(), Roles.AGENT);
            join(acme, newUser(), Roles.AGENT);

            mvc.perform(get(membersPath(acme)).param("size", "2").header(AUTH, bearer(agent)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.totalElements").value(3));
        }

        @Test
        void aNonMemberOfAnotherOrganizationGetsA404() throws Exception {
            User outsider = newUser();
            join(organization("Mine"), outsider, Roles.OWNER);
            Organization theirs = organization("Theirs");
            join(theirs, newUser(), Roles.OWNER);

            mvc.perform(get(membersPath(theirs)).header(AUTH, bearer(outsider)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404));
        }

        @Test
        void sizeAboveTheMaximumIsRejected() throws Exception {
            User agent = newUser();
            Organization acme = organization("Acme");
            join(acme, agent, Roles.AGENT);

            mvc.perform(get(membersPath(acme)).param("size", "101").header(AUTH, bearer(agent)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void aNegativePageIsRejected() throws Exception {
            User agent = newUser();
            Organization acme = organization("Acme");
            join(acme, agent, Roles.AGENT);

            mvc.perform(get(membersPath(acme)).param("page", "-1").header(AUTH, bearer(agent)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void anAnonymousCallerIsUnauthorized() throws Exception {
            Organization acme = organization("Acme");

            mvc.perform(get(membersPath(acme))).andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class ChangeRole {

        @Test
        void anOwnerPromotesAnAgentToAdmin() throws Exception {
            User owner = newUser();
            Organization acme = organization("Acme");
            join(acme, owner, Roles.OWNER);
            User agent = newUser();
            join(acme, agent, Roles.AGENT);

            mvc.perform(json(patch(memberPath(acme, membershipId(acme, agent))).header(AUTH, bearer(owner)),
                            roleBody(Roles.ADMIN)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value(Roles.ADMIN))
                    .andExpect(jsonPath("$.userId").value(agent.getId().toString()));
            assertEquals(Roles.ADMIN, roleOf(acme, agent));
        }

        @Test
        void anAdminPromotesAnAgentToAdmin() throws Exception {
            User admin = newUser();
            Organization acme = organization("Acme");
            join(acme, admin, Roles.ADMIN);
            User agent = newUser();
            join(acme, agent, Roles.AGENT);

            mvc.perform(json(patch(memberPath(acme, membershipId(acme, agent))).header(AUTH, bearer(admin)),
                            roleBody(Roles.ADMIN)))
                    .andExpect(status().isOk());
        }

        @Test
        void anAdminCannotGrantOwner() throws Exception {
            User admin = newUser();
            Organization acme = organization("Acme");
            join(acme, admin, Roles.ADMIN);
            User agent = newUser();
            join(acme, agent, Roles.AGENT);

            mvc.perform(json(patch(memberPath(acme, membershipId(acme, agent))).header(AUTH, bearer(admin)),
                            roleBody(Roles.OWNER)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403));
            assertEquals(Roles.AGENT, roleOf(acme, agent));
        }

        @Test
        void anAdminCannotTouchAnOwner() throws Exception {
            User admin = newUser();
            Organization acme = organization("Acme");
            join(acme, admin, Roles.ADMIN);
            User owner = newUser();
            join(acme, owner, Roles.OWNER);
            join(acme, newUser(), Roles.OWNER);

            mvc.perform(json(patch(memberPath(acme, membershipId(acme, owner))).header(AUTH, bearer(admin)),
                            roleBody(Roles.AGENT)))
                    .andExpect(status().isForbidden());
            assertEquals(Roles.OWNER, roleOf(acme, owner));
        }

        @Test
        void anAgentIsForbidden() throws Exception {
            User agent = newUser();
            Organization acme = organization("Acme");
            join(acme, agent, Roles.AGENT);
            User other = newUser();
            join(acme, other, Roles.AGENT);

            mvc.perform(json(patch(memberPath(acme, membershipId(acme, other))).header(AUTH, bearer(agent)),
                            roleBody(Roles.ADMIN)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void aRoleOutsideTheThreeIsABadRequest() throws Exception {
            User owner = newUser();
            Organization acme = organization("Acme");
            join(acme, owner, Roles.OWNER);
            User agent = newUser();
            join(acme, agent, Roles.AGENT);

            mvc.perform(json(patch(memberPath(acme, membershipId(acme, agent))).header(AUTH, bearer(owner)),
                            roleBody("SUPERUSER")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.role").exists());
        }

        @Test
        void aMembershipOfAnotherOrganizationIsA404() throws Exception {
            User owner = newUser();
            Organization acme = organization("Acme");
            join(acme, owner, Roles.OWNER);
            Organization theirs = organization("Theirs");
            User stranger = newUser();
            join(theirs, stranger, Roles.AGENT);

            mvc.perform(json(patch(memberPath(acme, membershipId(theirs, stranger))).header(AUTH, bearer(owner)),
                            roleBody(Roles.ADMIN)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404));
            assertEquals(Roles.AGENT, roleOf(theirs, stranger));
        }

        @Test
        void aNonMemberCallerIsA404() throws Exception {
            Organization acme = organization("Acme");
            join(acme, newUser(), Roles.OWNER);
            User agent = newUser();
            join(acme, agent, Roles.AGENT);
            User outsider = newUser();

            mvc.perform(json(patch(memberPath(acme, membershipId(acme, agent))).header(AUTH, bearer(outsider)),
                            roleBody(Roles.ADMIN)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void demotingTheLastOwnerIsUnprocessable() throws Exception {
            User owner = newUser();
            Organization acme = organization("Acme");
            join(acme, owner, Roles.OWNER);

            mvc.perform(json(patch(memberPath(acme, membershipId(acme, owner))).header(AUTH, bearer(owner)),
                            roleBody(Roles.ADMIN)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.status").value(422));
            assertEquals(Roles.OWNER, roleOf(acme, owner));
        }

        @Test
        void anAnonymousCallerIsUnauthorized() throws Exception {
            Organization acme = organization("Acme");

            mvc.perform(json(patch(memberPath(acme, UUID.randomUUID())), roleBody(Roles.ADMIN)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class Remove {

        @Test
        void anAdminRemovesAnAgent() throws Exception {
            User admin = newUser();
            Organization acme = organization("Acme");
            join(acme, admin, Roles.ADMIN);
            User agent = newUser();
            join(acme, agent, Roles.AGENT);

            mvc.perform(delete(memberPath(acme, membershipId(acme, agent))).header(AUTH, bearer(admin)))
                    .andExpect(status().isNoContent());
            assertFalse(memberships.findByOrganizationIdAndUserId(acme.getId(), agent.getId()).isPresent());
        }

        @Test
        void anAgentLeavesTheOrganizationThemselves() throws Exception {
            User agent = newUser();
            Organization acme = organization("Acme");
            join(acme, agent, Roles.AGENT);
            join(acme, newUser(), Roles.OWNER);

            mvc.perform(delete(memberPath(acme, membershipId(acme, agent))).header(AUTH, bearer(agent)))
                    .andExpect(status().isNoContent());
            assertFalse(memberships.findByOrganizationIdAndUserId(acme.getId(), agent.getId()).isPresent());
        }

        @Test
        void theLastOwnerCannotLeave() throws Exception {
            User owner = newUser();
            Organization acme = organization("Acme");
            join(acme, owner, Roles.OWNER);
            join(acme, newUser(), Roles.AGENT);

            mvc.perform(delete(memberPath(acme, membershipId(acme, owner))).header(AUTH, bearer(owner)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.status").value(422));
            assertEquals(Roles.OWNER, roleOf(acme, owner));
        }

        @Test
        void anAgentCannotRemoveSomeoneElse() throws Exception {
            User agent = newUser();
            Organization acme = organization("Acme");
            join(acme, agent, Roles.AGENT);
            User other = newUser();
            join(acme, other, Roles.AGENT);

            mvc.perform(delete(memberPath(acme, membershipId(acme, other))).header(AUTH, bearer(agent)))
                    .andExpect(status().isForbidden());
            assertEquals(Roles.AGENT, roleOf(acme, other));
        }

        @Test
        void anAdminCannotRemoveAnOwner() throws Exception {
            User admin = newUser();
            Organization acme = organization("Acme");
            join(acme, admin, Roles.ADMIN);
            User owner = newUser();
            join(acme, owner, Roles.OWNER);
            join(acme, newUser(), Roles.OWNER);

            mvc.perform(delete(memberPath(acme, membershipId(acme, owner))).header(AUTH, bearer(admin)))
                    .andExpect(status().isForbidden());
            assertEquals(Roles.OWNER, roleOf(acme, owner));
        }

        @Test
        void aMembershipOfAnotherOrganizationIsA404() throws Exception {
            User owner = newUser();
            Organization acme = organization("Acme");
            join(acme, owner, Roles.OWNER);
            Organization theirs = organization("Theirs");
            User stranger = newUser();
            join(theirs, stranger, Roles.AGENT);

            mvc.perform(delete(memberPath(acme, membershipId(theirs, stranger))).header(AUTH, bearer(owner)))
                    .andExpect(status().isNotFound());
            assertEquals(Roles.AGENT, roleOf(theirs, stranger));
        }

        @Test
        void anAnonymousCallerIsUnauthorized() throws Exception {
            Organization acme = organization("Acme");

            mvc.perform(delete(memberPath(acme, UUID.randomUUID()))).andExpect(status().isUnauthorized());
        }
    }
}
