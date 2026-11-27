package com.connecta.backend.org.controller;

import com.connecta.backend.support.SharedPostgres;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.connecta.backend.config.JwtProperties;
import com.connecta.backend.org.domain.Invitation;
import com.connecta.backend.org.domain.Membership;
import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.org.repository.InvitationRepository;
import com.connecta.backend.org.repository.MembershipRepository;
import com.connecta.backend.org.repository.OrganizationRepository;
import com.connecta.backend.org.service.InvitationTokenGenerator;
import com.connecta.backend.support.RandomJwtSecret;
import com.connecta.backend.support.TestClock;
import com.connecta.backend.support.TestTokens;
import com.connecta.backend.support.TestUsers;
import com.connecta.backend.user.domain.Role;
import com.connecta.backend.user.domain.Roles;
import com.connecta.backend.user.domain.User;
import com.connecta.backend.user.repository.RoleRepository;
import com.connecta.backend.user.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The invitation endpoints against real PostgreSQL (Flyway runs the migrations). Each endpoint has an
 * allowed role and a denied role, plus the 401, cross-tenant 404 and accept-by-wrong-email cases.
 * Skipped without Docker.
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false"})
@AutoConfigureMockMvc
@Import(TestClock.class)
@ContextConfiguration(initializers = RandomJwtSecret.class)
class InvitationControllerIntegrationTests extends SharedPostgres {

    private static final String ORGANIZATIONS = "/api/organizations";
    private static final String ACCEPT = "/api/invitations/accept";
    private static final String AUTH = "Authorization";
    private static final Duration SEVEN_DAYS = Duration.ofDays(7);

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
    InvitationRepository invitations;

    @Autowired
    InvitationTokenGenerator tokenGenerator;

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static String inviteBody(String email, String role) {
        return "{\"email\":\"" + email + "\",\"role\":\"" + role + "\"}";
    }

    private static String acceptBody(String token) {
        return "{\"token\":\"" + token + "\"}";
    }

    private static String invitesPath(Organization organization) {
        return ORGANIZATIONS + "/" + organization.getId() + "/invitations";
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

    private void join(Organization organization, User user, String roleName) {
        memberships.saveAndFlush(new Membership(organization, user, role(roleName)));
    }

    private String bearer(User user) {
        return TestTokens.bearer(jwtProperties, user);
    }

    /** Creates an invitation through the API as the caller and returns the raw token it answered with. */
    private String inviteToken(User caller, Organization organization, String email, String roleName)
            throws Exception {
        String body = mvc.perform(json(post(invitesPath(organization)).header(AUTH, bearer(caller)),
                        inviteBody(email, roleName)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    /** An invitation written straight to the database, so tests can make it expired or revoked. */
    private Invitation storedInvitation(Organization organization, User invitedBy, String email, String token,
            Instant createdAt, Instant expiresAt) {
        Invitation invitation = new Invitation(organization, email, role(Roles.AGENT), invitedBy,
                tokenGenerator.hash(token), createdAt, expiresAt);
        return invitations.saveAndFlush(invitation);
    }

    private Invitation storedPending(Organization organization, User invitedBy, String email, String token) {
        return storedInvitation(organization, invitedBy, email, token, TestClock.NOW,
                TestClock.NOW.plus(SEVEN_DAYS));
    }

    private void assertNoMembership(Organization organization, User user) {
        assertTrue(memberships.findByOrganizationIdAndUserId(organization.getId(), user.getId()).isEmpty());
    }

    @Nested
    class Create {

        @Test
        void anAdminInvitesAnAdminAndGetsTheTokenOnceWithALocation() throws Exception {
            User owner = newUser();
            Organization acme = organization("Acme");
            join(acme, owner, Roles.ADMIN);
            String invitee = "invitee" + UUID.randomUUID() + "@example.com";

            String body = mvc.perform(json(post(invitesPath(acme)).header(AUTH, bearer(owner)),
                            inviteBody(invitee, Roles.ADMIN)))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", containsString(invitesPath(acme) + "/")))
                    .andExpect(jsonPath("$.invitation.email").value(invitee))
                    .andExpect(jsonPath("$.invitation.role").value("ADMIN"))
                    .andExpect(jsonPath("$.invitation.status").value("PENDING"))
                    .andExpect(jsonPath("$.invitation.tokenHash").doesNotExist())
                    .andExpect(jsonPath("$.token").isString())
                    .andReturn().getResponse().getContentAsString();

            String token = JsonPath.read(body, "$.token");
            Invitation stored = invitations.findByTokenHash(tokenGenerator.hash(token)).orElseThrow();
            assertEquals(acme.getId(), stored.getOrganization().getId());
            assertFalse(body.contains(stored.getTokenHash()));
        }

        @Test
        void anAdminInvitesAnAgent() throws Exception {
            User admin = newUser();
            Organization acme = organization("Acme");
            join(acme, admin, Roles.ADMIN);

            inviteToken(admin, acme, "agent" + UUID.randomUUID() + "@example.com", Roles.AGENT);
        }

        @Test
        void inviteeRoleOwnerIsABadRequest() throws Exception {
            // OWNER is the system owner, never an organization role.
            User admin = newUser();
            Organization acme = organization("Acme");
            join(acme, admin, Roles.ADMIN);

            mvc.perform(json(post(invitesPath(acme)).header(AUTH, bearer(admin)),
                            inviteBody("boss@example.com", "OWNER")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.role").exists());
            assertTrue(invitations.findAll().stream()
                    .noneMatch(i -> i.getOrganization().getId().equals(acme.getId())));
        }

        @Test
        void anAgentIsForbidden() throws Exception {
            User agent = newUser();
            Organization acme = organization("Acme");
            join(acme, agent, Roles.AGENT);

            mvc.perform(json(post(invitesPath(acme)).header(AUTH, bearer(agent)),
                            inviteBody("someone@example.com", Roles.AGENT)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void anAdminOfAnotherOrganizationGetsA404() throws Exception {
            User admin = newUser();
            join(organization("Mine"), admin, Roles.ADMIN);
            Organization theirs = organization("Theirs");

            mvc.perform(json(post(invitesPath(theirs)).header(AUTH, bearer(admin)),
                            inviteBody("someone@example.com", Roles.AGENT)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void anEmailAlreadyPendingInThisOrganizationIsAConflict() throws Exception {
            User admin = newUser();
            Organization acme = organization("Acme");
            join(acme, admin, Roles.ADMIN);
            inviteToken(admin, acme, "dup@example.com", Roles.AGENT);

            mvc.perform(json(post(invitesPath(acme)).header(AUTH, bearer(admin)),
                            inviteBody("dup@example.com", Roles.AGENT)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409));
        }

        @Test
        void anEmailThatIsAlreadyAMemberIsAConflict() throws Exception {
            User admin = newUser();
            Organization acme = organization("Acme");
            join(acme, admin, Roles.ADMIN);
            User existing = newUser();
            join(acme, existing, Roles.AGENT);

            mvc.perform(json(post(invitesPath(acme)).header(AUTH, bearer(admin)),
                            inviteBody(existing.getEmail(), Roles.AGENT)))
                    .andExpect(status().isConflict());
        }

        @Test
        void theEmailIsStoredInLowerCase() throws Exception {
            User admin = newUser();
            Organization acme = organization("Acme");
            join(acme, admin, Roles.ADMIN);

            mvc.perform(json(post(invitesPath(acme)).header(AUTH, bearer(admin)),
                            inviteBody("Mixed.Case@Example.COM", Roles.AGENT)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.invitation.email").value("mixed.case@example.com"));
        }

        @Test
        void invalidEmailOrRoleIsRejectedWithTheFields() throws Exception {
            User admin = newUser();
            Organization acme = organization("Acme");
            join(acme, admin, Roles.ADMIN);

            mvc.perform(json(post(invitesPath(acme)).header(AUTH, bearer(admin)),
                            inviteBody("not-an-email", "SUPERUSER")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.email").exists())
                    .andExpect(jsonPath("$.errors.role").exists());
        }

        @Test
        void anonymousCallerIsUnauthorized() throws Exception {
            mvc.perform(json(post(invitesPath(organization("Acme"))), inviteBody("a@example.com", Roles.AGENT)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class ListForAdmins {

        @Test
        void anAdminSeesOnlyThisOrganizationsInvitationsWithoutTokens() throws Exception {
            User admin = newUser();
            Organization acme = organization("Acme");
            join(acme, admin, Roles.ADMIN);
            storedPending(acme, admin, "one@example.com", "token-one");
            storedPending(acme, admin, "two@example.com", "token-two");
            storedPending(organization("Other"), admin, "elsewhere@example.com", "token-three");

            mvc.perform(get(invitesPath(acme)).header(AUTH, bearer(admin)).param("size", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].token").doesNotExist())
                    .andExpect(jsonPath("$.content[0].tokenHash").doesNotExist())
                    .andExpect(jsonPath("$.totalElements").value(2))
                    .andExpect(jsonPath("$.size").value(1));
        }

        @Test
        void anAgentIsForbidden() throws Exception {
            User agent = newUser();
            Organization acme = organization("Acme");
            join(acme, agent, Roles.AGENT);

            mvc.perform(get(invitesPath(acme)).header(AUTH, bearer(agent))).andExpect(status().isForbidden());
        }

        @Test
        void aNonMemberGetsA404() throws Exception {
            mvc.perform(get(invitesPath(organization("Acme"))).header(AUTH, bearer(newUser())))
                    .andExpect(status().isNotFound());
        }

        @Test
        void anonymousCallerIsUnauthorized() throws Exception {
            mvc.perform(get(invitesPath(organization("Acme")))).andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class Revoke {

        @Test
        void anAdminRevokesAPendingInvitationAndItCannotBeAccepted() throws Exception {
            User admin = newUser();
            Organization acme = organization("Acme");
            join(acme, admin, Roles.ADMIN);
            User invitee = newUser();
            String token = inviteToken(admin, acme, invitee.getEmail(), Roles.AGENT);
            UUID invitationId = invitations.findByTokenHash(tokenGenerator.hash(token)).orElseThrow().getId();

            mvc.perform(delete(invitesPath(acme) + "/" + invitationId).header(AUTH, bearer(admin)))
                    .andExpect(status().isNoContent());

            mvc.perform(json(post(ACCEPT).header(AUTH, bearer(invitee)), acceptBody(token)))
                    .andExpect(status().is(422));
        }

        @Test
        void anAgentIsForbiddenAndTheInvitationStaysPending() throws Exception {
            User owner = newUser();
            User agent = newUser();
            Organization acme = organization("Acme");
            join(acme, owner, Roles.ADMIN);
            join(acme, agent, Roles.AGENT);
            Invitation pending = storedPending(acme, owner, "x@example.com", "token-agent");

            mvc.perform(delete(invitesPath(acme) + "/" + pending.getId()).header(AUTH, bearer(agent)))
                    .andExpect(status().isForbidden());
            assertEquals(Invitation.Status.PENDING, invitations.findById(pending.getId()).orElseThrow().getStatus());
        }

        @Test
        void anotherOrganizationsInvitationIsNotFoundThroughThisOrganization() throws Exception {
            User admin = newUser();
            Organization mine = organization("Mine");
            Organization theirs = organization("Theirs");
            join(mine, admin, Roles.ADMIN);
            join(theirs, newUser(), Roles.ADMIN);
            Invitation foreign = storedPending(theirs, admin, "y@example.com", "token-foreign");

            mvc.perform(delete(invitesPath(mine) + "/" + foreign.getId()).header(AUTH, bearer(admin)))
                    .andExpect(status().isNotFound());
            assertEquals(Invitation.Status.PENDING, invitations.findById(foreign.getId()).orElseThrow().getStatus());
        }

        @Test
        void anAcceptedInvitationCannotBeRevoked() throws Exception {
            User admin = newUser();
            Organization acme = organization("Acme");
            join(acme, admin, Roles.ADMIN);
            User invitee = newUser();
            String token = inviteToken(admin, acme, invitee.getEmail(), Roles.AGENT);
            mvc.perform(json(post(ACCEPT).header(AUTH, bearer(invitee)), acceptBody(token)))
                    .andExpect(status().isOk());
            UUID invitationId = invitations.findByTokenHash(tokenGenerator.hash(token)).orElseThrow().getId();

            mvc.perform(delete(invitesPath(acme) + "/" + invitationId).header(AUTH, bearer(admin)))
                    .andExpect(status().isConflict());
        }

        @Test
        void aNonMemberGetsA404() throws Exception {
            Organization acme = organization("Acme");
            Invitation pending = storedPending(acme, newUser(), "z@example.com", "token-non-member");

            mvc.perform(delete(invitesPath(acme) + "/" + pending.getId()).header(AUTH, bearer(newUser())))
                    .andExpect(status().isNotFound());
        }

        @Test
        void anonymousCallerIsUnauthorized() throws Exception {
            mvc.perform(delete(invitesPath(organization("Acme")) + "/" + UUID.randomUUID()))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class Accept {

        @Test
        void theMatchingAccountJoinsWithTheInvitedRole() throws Exception {
            User admin = newUser();
            Organization acme = organization("Acme");
            join(acme, admin, Roles.ADMIN);
            User invitee = newUser();
            String token = inviteToken(admin, acme, invitee.getEmail(), Roles.ADMIN);

            mvc.perform(json(post(ACCEPT).header(AUTH, bearer(invitee)), acceptBody(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(acme.getId().toString()))
                    .andExpect(jsonPath("$.name").value("Acme"))
                    .andExpect(jsonPath("$.callerRole").value("ADMIN"));

            assertEquals(Roles.ADMIN, memberships.findByOrganizationIdAndUserId(acme.getId(), invitee.getId())
                    .orElseThrow().getRole().getName());
            assertEquals(Invitation.Status.ACCEPTED,
                    invitations.findByTokenHash(tokenGenerator.hash(token)).orElseThrow().getStatus());
        }

        @Test
        void anotherAccountWithTheTokenGetsTheSame404AndNoMembership() throws Exception {
            User admin = newUser();
            Organization acme = organization("Acme");
            join(acme, admin, Roles.ADMIN);
            String token = inviteToken(admin, acme, "intended@example.com", Roles.AGENT);
            User stranger = newUser();

            mvc.perform(json(post(ACCEPT).header(AUTH, bearer(stranger)), acceptBody(token)))
                    .andExpect(status().isNotFound());
            assertNoMembership(acme, stranger);
            assertEquals(Invitation.Status.PENDING,
                    invitations.findByTokenHash(tokenGenerator.hash(token)).orElseThrow().getStatus());
        }

        @Test
        void anUnknownTokenIsNotFound() throws Exception {
            mvc.perform(json(post(ACCEPT).header(AUTH, bearer(newUser())), acceptBody("no-such-token")))
                    .andExpect(status().isNotFound());
        }

        @Test
        void acceptingTwiceIsAConflict() throws Exception {
            User admin = newUser();
            Organization acme = organization("Acme");
            join(acme, admin, Roles.ADMIN);
            User invitee = newUser();
            String token = inviteToken(admin, acme, invitee.getEmail(), Roles.AGENT);
            mvc.perform(json(post(ACCEPT).header(AUTH, bearer(invitee)), acceptBody(token)))
                    .andExpect(status().isOk());

            mvc.perform(json(post(ACCEPT).header(AUTH, bearer(invitee)), acceptBody(token)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409));
        }

        @Test
        void anExpiredInvitationIsUnprocessable() throws Exception {
            User admin = newUser();
            Organization acme = organization("Acme");
            join(acme, admin, Roles.ADMIN);
            User invitee = newUser();
            String token = "expired-token";
            storedInvitation(acme, admin, invitee.getEmail(), token,
                    TestClock.NOW.minus(SEVEN_DAYS).minusSeconds(60), TestClock.NOW.minusSeconds(60));

            mvc.perform(json(post(ACCEPT).header(AUTH, bearer(invitee)), acceptBody(token)))
                    .andExpect(status().is(422))
                    .andExpect(jsonPath("$.status").value(422));
            assertNoMembership(acme, invitee);
        }

        @Test
        void aRevokedInvitationIsUnprocessable() throws Exception {
            User admin = newUser();
            Organization acme = organization("Acme");
            join(acme, admin, Roles.ADMIN);
            User invitee = newUser();
            String token = "revoked-token";
            Invitation revoked = storedPending(acme, admin, invitee.getEmail(), token);
            revoked.revoke();
            invitations.saveAndFlush(revoked);

            mvc.perform(json(post(ACCEPT).header(AUTH, bearer(invitee)), acceptBody(token)))
                    .andExpect(status().is(422));
            assertNoMembership(acme, invitee);
        }

        @Test
        void aBlankTokenIsRejected() throws Exception {
            mvc.perform(json(post(ACCEPT).header(AUTH, bearer(newUser())), acceptBody("  ")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.token").exists());
        }

        @Test
        void anonymousCallerIsUnauthorized() throws Exception {
            mvc.perform(json(post(ACCEPT), acceptBody("any-token"))).andExpect(status().isUnauthorized());
        }
    }
}
