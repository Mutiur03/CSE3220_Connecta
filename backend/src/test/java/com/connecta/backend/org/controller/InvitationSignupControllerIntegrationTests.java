package com.connecta.backend.org.controller;

import com.connecta.backend.support.SharedPostgres;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.connecta.backend.org.domain.Invitation;
import com.connecta.backend.org.domain.Membership;
import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.org.repository.InvitationRepository;
import com.connecta.backend.org.repository.MembershipRepository;
import com.connecta.backend.org.repository.OrganizationRepository;
import com.connecta.backend.org.service.InvitationTokenGenerator;
import com.connecta.backend.support.TestTokens;
import com.connecta.backend.user.domain.Roles;
import com.connecta.backend.user.domain.User;
import com.connecta.backend.user.repository.RoleRepository;
import com.connecta.backend.user.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import com.connecta.backend.config.JwtProperties;
import com.connecta.backend.support.RandomJwtSecret;
import com.connecta.backend.support.TestClock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The two public invitation endpoints, preview and accept-new, against real PostgreSQL. The token is the
 * credential, so neither endpoint takes an access token. Skipped without Docker.
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false"})
@AutoConfigureMockMvc
@Import(TestClock.class)
@ContextConfiguration(initializers = RandomJwtSecret.class)
class InvitationSignupControllerIntegrationTests extends SharedPostgres {

    private static final String PREVIEW = "/api/invitations/preview";
    private static final String ACCEPT_NEW = "/api/invitations/accept-new";
    private static final String PASSWORD = "correct-password";

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

    private static String tokenBody(String token) {
        return "{\"token\":\"" + token + "\"}";
    }

    private static String signupBody(String token, String password) {
        return "{\"token\":\"" + token + "\",\"fullName\":\"New Person\",\"password\":\"" + password + "\"}";
    }

    private String newEmail() {
        return "invitee" + UUID.randomUUID() + "@example.com";
    }

    private User admin(Organization organization) {
        User user = new User("a" + UUID.randomUUID() + "@example.com", "hash", "Jane Admin");
        user.addRole(roles.findByName(Roles.ADMIN).orElseThrow());
        user = users.saveAndFlush(user);
        memberships.saveAndFlush(new Membership(organization, user, roles.findByName(Roles.ADMIN).orElseThrow()));
        return user;
    }

    private Organization organization() {
        return organizations.saveAndFlush(new Organization("Acme", "org-" + UUID.randomUUID()));
    }

    /** An invitation written straight to the database, so tests can make it expired or revoked. */
    private Invitation invitation(Organization organization, String email, String roleName, Instant expiresAt) {
        User inviter = admin(organization);
        String token = "tok-" + UUID.randomUUID();
        Invitation invitation = new Invitation(organization, email, roles.findByName(roleName).orElseThrow(),
                inviter, tokenGenerator.hash(token), TestClock.NOW.minus(Duration.ofDays(1)), expiresAt);
        invitations.saveAndFlush(invitation);
        tokens.put(invitation.getId(), token);
        return invitation;
    }

    private final java.util.Map<UUID, String> tokens = new java.util.HashMap<>();

    private String tokenOf(Invitation invitation) {
        return tokens.get(invitation.getId());
    }

    private Invitation pending(Organization organization, String email, String roleName) {
        return invitation(organization, email, roleName, TestClock.NOW.plus(Duration.ofDays(6)));
    }

    @Nested
    class Preview {

        @Test
        void showsTheInvitationToAnonymousVisitors() throws Exception {
            Organization acme = organization();
            String email = newEmail();
            Invitation invitation = pending(acme, email, Roles.AGENT);

            mvc.perform(json(post(PREVIEW), tokenBody(tokenOf(invitation))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value(email))
                    .andExpect(jsonPath("$.organizationName").value("Acme"))
                    .andExpect(jsonPath("$.role").value("AGENT"))
                    .andExpect(jsonPath("$.inviterName").value("Jane Admin"))
                    .andExpect(jsonPath("$.accountExists").value(false))
                    .andExpect(jsonPath("$.tokenHash").doesNotExist());
        }

        @Test
        void reportsAnExistingAccount() throws Exception {
            Organization acme = organization();
            User existing = admin(acme);
            Invitation invitation = pending(acme, existing.getEmail(), Roles.AGENT);

            mvc.perform(json(post(PREVIEW), tokenBody(tokenOf(invitation))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accountExists").value(true));
        }

        @Test
        void aBadAccessTokenDoesNotBlockIt() throws Exception {
            Invitation invitation = pending(organization(), newEmail(), Roles.AGENT);

            mvc.perform(json(post(PREVIEW).header("Authorization", "Bearer garbage"), tokenBody(tokenOf(invitation))))
                    .andExpect(status().isOk());
        }

        @Test
        void unknownExpiredRevokedAndAcceptedTokensAllReadAsTheSame404() throws Exception {
            Organization acme = organization();
            Invitation expired = invitation(acme, newEmail(), Roles.AGENT, TestClock.NOW.minus(Duration.ofMinutes(1)));
            Invitation revoked = pending(acme, newEmail(), Roles.AGENT);
            revoked.revoke();
            invitations.saveAndFlush(revoked);
            Invitation accepted = pending(acme, newEmail(), Roles.AGENT);
            accepted.accept(TestClock.FIXED);
            invitations.saveAndFlush(accepted);

            for (String token : new String[] {"no-such-token", tokenOf(expired), tokenOf(revoked), tokenOf(accepted)}) {
                mvc.perform(json(post(PREVIEW), tokenBody(token)))
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.status").value(404))
                        .andExpect(jsonPath("$.detail").value("Invitation not found"));
            }
        }

        @Test
        void aMissingTokenIsABadRequest() throws Exception {
            mvc.perform(json(post(PREVIEW), "{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.token").exists());
        }
    }

    @Nested
    class AcceptNew {

        @Test
        void anAgentInviteCreatesAnAgentAccountJoinsAndSignsIn() throws Exception {
            Organization acme = organization();
            String email = newEmail();
            Invitation invitation = pending(acme, email, Roles.AGENT);

            String body = mvc.perform(json(post(ACCEPT_NEW), signupBody(tokenOf(invitation), PASSWORD)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.user.email").value(email))
                    .andExpect(jsonPath("$.user.roles[0]").value(Roles.AGENT))
                    .andExpect(jsonPath("$.user.roles.length()").value(1))
                    .andExpect(jsonPath("$.refreshToken").isString())
                    .andReturn().getResponse().getContentAsString();

            User created = users.findByEmail(email).orElseThrow();
            assertEquals(Set.of(Roles.AGENT), created.getRoleNames());
            assertTrue(!created.getPasswordHash().equals(PASSWORD));
            Membership membership = memberships.findByOrganizationIdAndUserId(acme.getId(), created.getId()).orElseThrow();
            assertEquals(Roles.AGENT, membership.getRole().getName());
            assertEquals(Invitation.Status.ACCEPTED, invitations.findById(invitation.getId()).orElseThrow().getStatus());

            String accessToken = JsonPath.read(body, "$.accessToken");
            mvc.perform(get("/api/organizations").header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(acme.getId().toString()))
                    .andExpect(jsonPath("$.content[0].callerRole").value(Roles.AGENT));
        }

        @Test
        void anAdminInviteCreatesAnAdminAccount() throws Exception {
            Organization acme = organization();
            String email = newEmail();
            Invitation invitation = pending(acme, email, Roles.ADMIN);

            mvc.perform(json(post(ACCEPT_NEW), signupBody(tokenOf(invitation), PASSWORD)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.user.roles[0]").value(Roles.ADMIN));
            User created = users.findByEmail(email).orElseThrow();
            assertEquals(Roles.ADMIN,
                    memberships.findByOrganizationIdAndUserId(acme.getId(), created.getId()).orElseThrow()
                            .getRole().getName());
        }

        @Test
        void aTokenCanBeUsedOnlyOnce() throws Exception {
            Invitation invitation = pending(organization(), newEmail(), Roles.AGENT);
            mvc.perform(json(post(ACCEPT_NEW), signupBody(tokenOf(invitation), PASSWORD)))
                    .andExpect(status().isCreated());

            mvc.perform(json(post(ACCEPT_NEW), signupBody(tokenOf(invitation), PASSWORD)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409));
        }

        @Test
        void anEmailWithAnAccountMustSignInAndTheInvitationStaysPending() throws Exception {
            Organization acme = organization();
            User existing = admin(acme);
            Invitation invitation = pending(acme, existing.getEmail(), Roles.AGENT);

            mvc.perform(json(post(ACCEPT_NEW), signupBody(tokenOf(invitation), PASSWORD)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409));
            assertEquals(Invitation.Status.PENDING, invitations.findById(invitation.getId()).orElseThrow().getStatus());
        }

        @Test
        void anUnknownTokenIsNotFound() throws Exception {
            mvc.perform(json(post(ACCEPT_NEW), signupBody("no-such-token", PASSWORD)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404));
        }

        @Test
        void anExpiredInvitationIsUnprocessableAndCreatesNoAccount() throws Exception {
            String email = newEmail();
            Invitation expired = invitation(organization(), email, Roles.AGENT, TestClock.NOW.minus(Duration.ofMinutes(1)));

            mvc.perform(json(post(ACCEPT_NEW), signupBody(tokenOf(expired), PASSWORD)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.status").value(422));
            assertTrue(users.findByEmail(email).isEmpty());
        }

        @Test
        void aRevokedInvitationIsUnprocessable() throws Exception {
            Invitation revoked = pending(organization(), newEmail(), Roles.AGENT);
            revoked.revoke();
            invitations.saveAndFlush(revoked);

            mvc.perform(json(post(ACCEPT_NEW), signupBody(tokenOf(revoked), PASSWORD)))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        void invalidInputIsABadRequest() throws Exception {
            mvc.perform(json(post(ACCEPT_NEW), signupBody("tok", "short")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.password").exists());
            mvc.perform(json(post(ACCEPT_NEW), "{\"token\":\"tok\",\"fullName\":\"\",\"password\":\"" + PASSWORD + "\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.fullName").exists());
        }
    }

    @Nested
    class EndToEnd {

        @Test
        void anAdminInvitesThroughTheApiAndTheInviteeCanPreviewAndAccept() throws Exception {
            Organization acme = organization();
            User inviter = admin(acme);
            String email = newEmail();

            String created = mvc.perform(json(post("/api/organizations/" + acme.getId() + "/invitations")
                                    .header("Authorization", TestTokens.bearer(jwtProperties, inviter)),
                            "{\"email\":\"" + email + "\",\"role\":\"AGENT\"}"))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            String token = JsonPath.read(created, "$.token");

            mvc.perform(json(post(PREVIEW), tokenBody(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value(email))
                    .andExpect(jsonPath("$.inviterName").value("Jane Admin"));
            mvc.perform(json(post(ACCEPT_NEW), signupBody(token, PASSWORD)))
                    .andExpect(status().isCreated());
        }
    }
}
