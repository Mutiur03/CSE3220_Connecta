package com.connecta.backend.org.controller;

import com.connecta.backend.support.SharedPostgres;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.connecta.backend.config.JwtProperties;
import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.inbox.domain.Conversation;
import com.connecta.backend.inbox.domain.ConversationStatus;
import com.connecta.backend.inbox.domain.AssignmentSource;
import com.connecta.backend.inbox.repository.ConversationRepository;
import com.connecta.backend.org.domain.AvailabilityInterval;
import com.connecta.backend.org.domain.Membership;
import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.org.repository.AvailabilityIntervalRepository;
import com.connecta.backend.org.repository.MembershipRepository;
import com.connecta.backend.org.repository.OrganizationRepository;
import com.connecta.backend.org.service.PresenceService;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The availability endpoints against real PostgreSQL (Flyway runs the migrations). Each endpoint has an allowed
 * role and a denied role, plus 401, 400 and the cross-tenant 404. Skipped
 * without Docker.
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false"})
@AutoConfigureMockMvc
@Import(TestClock.class)
@ContextConfiguration(initializers = RandomJwtSecret.class)
class AvailabilityControllerIntegrationTests extends SharedPostgres {

    private static final String AUTH = "Authorization";

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
    ConversationRepository conversations;

    @Autowired
    AvailabilityIntervalRepository intervals;

    @Autowired
    PresenceService presence;

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

    @Nested
    class SetAssignable {

        private static final String ON = "{\"assignable\":true}";

        private String url(Organization org, Membership member) {
            return "/api/organizations/" + org.getId() + "/members/" + member.getId() + "/assignable";
        }

        @Test
        void anAdminSwitchesAMemberAndTheListShowsIt() throws Exception {
            User admin = newUser();
            User agent = newUser();
            Organization acme = organization("Acme");
            join(acme, admin, Roles.ADMIN);
            Membership seat = join(acme, agent, Roles.AGENT);

            mvc.perform(json(put(url(acme, seat)), ON).header(AUTH, bearer(admin)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.membershipId").value(seat.getId().toString()))
                    .andExpect(jsonPath("$.assignable").value(true));
            mvc.perform(get(path(acme, "")).header(AUTH, bearer(admin)).param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[?(@.membershipId=='" + seat.getId() + "')].assignable")
                            .value(true));
            mvc.perform(json(put(url(acme, seat)), "{\"assignable\":false}").header(AUTH, bearer(admin)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.assignable").value(false));
        }

        @Test
        void anInvalidBodyIsRejected() throws Exception {
            User admin = newUser();
            Organization acme = organization("Acme");
            Membership seat = join(acme, admin, Roles.ADMIN);

            mvc.perform(json(put(url(acme, seat)), "{}").header(AUTH, bearer(admin)))
                    .andExpect(status().isBadRequest());
            mvc.perform(json(put(url(acme, seat)), "{\"assignable\":\"yes\"}").header(AUTH, bearer(admin)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void anAgentIsForbidden() throws Exception {
            User agent = newUser();
            Organization acme = organization("Acme");
            Membership seat = join(acme, agent, Roles.AGENT);

            mvc.perform(json(put(url(acme, seat)), ON).header(AUTH, bearer(agent)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void aMembershipOfAnotherOrganizationIsNotFound() throws Exception {
            User admin = newUser();
            Organization mine = organization("Mine");
            join(mine, admin, Roles.ADMIN);
            Membership foreign = join(organization("Theirs"), newUser(), Roles.AGENT);

            mvc.perform(json(put(url(mine, foreign)), ON).header(AUTH, bearer(admin)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void anAdminOfAnotherOrganizationGetsA404() throws Exception {
            User outsider = newUser();
            join(organization("Mine"), outsider, Roles.ADMIN);
            Organization theirs = organization("Theirs");
            Membership seat = join(theirs, newUser(), Roles.AGENT);

            mvc.perform(json(put(url(theirs, seat)), ON).header(AUTH, bearer(outsider)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void anAnonymousCallerIsUnauthorized() throws Exception {
            Organization acme = organization("Acme");
            Membership seat = join(acme, newUser(), Roles.AGENT);

            mvc.perform(json(put(url(acme, seat)), ON)).andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class ListAll {

        @Test
        void anAdminSeesEveryMemberAndMissingRowsAreNotAssignable() throws Exception {
            User admin = newUser();
            User agent = newUser();
            Organization acme = organization("Acme");
            join(acme, admin, Roles.ADMIN);
            join(acme, agent, Roles.AGENT);
            mvc.perform(get(path(acme, "")).header(AUTH, bearer(admin)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(2))
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.size").value(20))
                    .andExpect(jsonPath("$.content[?(@.userId=='" + agent.getId() + "')].assignable").value(false))
                    .andExpect(jsonPath("$.content[?(@.userId=='" + admin.getId() + "')].assignable").value(false))
                    .andExpect(jsonPath("$.content[0].status").doesNotExist());
        }

        @Test
        void aConnectedMemberIsOnlineAndTheOthersAreNot() throws Exception {
            User admin = newUser();
            User agent = newUser();
            Organization acme = organization("Acme");
            join(acme, admin, Roles.ADMIN);
            join(acme, agent, Roles.AGENT);
            presence.connected(acme.getId(), agent.getId());

            mvc.perform(get(path(acme, "")).header(AUTH, bearer(admin)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[?(@.userId=='" + agent.getId() + "')].online").value(true))
                    .andExpect(jsonPath("$.content[?(@.userId=='" + admin.getId() + "')].online").value(false));
            assertTrue(intervals.findByMembershipUserIdAndEndedAtIsNull(agent.getId()).size() == 1);
        }

        @Test
        void eachMemberCountsOnlyTheirOpenConversationsInThisOrganization() throws Exception {
            User admin = newUser();
            User agent = newUser();
            Organization acme = organization("Acme");
            join(acme, admin, Roles.ADMIN);
            Membership agentSeat = join(acme, agent, Roles.AGENT);
            assignedConversation(acme, agentSeat, "t1", ConversationStatus.OPEN);
            assignedConversation(acme, agentSeat, "t2", ConversationStatus.OPEN);
            assignedConversation(acme, agentSeat, "t3", ConversationStatus.CLOSED);
            Organization other = organization("Other");
            assignedConversation(other, join(other, newUser(), Roles.AGENT), "t4", ConversationStatus.OPEN);

            mvc.perform(get(path(acme, "")).header(AUTH, bearer(admin)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[?(@.userId=='" + agent.getId() + "')].openConversations")
                            .value(2))
                    .andExpect(jsonPath("$.content[?(@.userId=='" + admin.getId() + "')].openConversations")
                            .value(0));
        }

        private void assignedConversation(Organization org, Membership member, String thread,
                ConversationStatus status) {
            Conversation conversation = new Conversation(org, Channel.MESSENGER, thread);
            conversation.changeStatus(status);
            conversation.assignTo(member, null, AssignmentSource.MANUAL);
            conversations.saveAndFlush(conversation);
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

    @Nested
    class Report {

        private static final String RANGE_FROM = "2026-11-13";
        private static final String RANGE_TO = "2026-11-16";

        private org.springframework.test.web.servlet.ResultActions report(Organization org, User caller,
                String from, String to) throws Exception {
            MockHttpServletRequestBuilder request = get(path(org, "/report")).header(AUTH, bearer(caller));
            if (from != null) {
                request.param("from", from);
            }
            if (to != null) {
                request.param("to", to);
            }
            return mvc.perform(request);
        }

        private Organization acmeWith(User admin) {
            Organization acme = organization("Acme");
            join(acme, admin, Roles.ADMIN);
            return acme;
        }

        @Test
        void anAdminSeesPerDaySecondsForEveryMemberZeroFilled() throws Exception {
            User admin = newUser();
            User agent = newUser();
            Organization acme = acmeWith(admin);
            Membership seat = join(acme, agent, Roles.AGENT);
            closed(seat, "2026-11-13T23:00:00Z", "2026-11-14T02:00:00Z");
            intervals.saveAndFlush(new AvailabilityInterval(seat, Instant.parse("2026-11-15T10:00:00Z")));

            report(acme, admin, RANGE_FROM, RANGE_TO)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.from").value(RANGE_FROM))
                    .andExpect(jsonPath("$.to").value(RANGE_TO))
                    .andExpect(jsonPath("$.totalElements").value(2))
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.size").value(20))
                    .andExpect(jsonPath("$.content[?(@.userId=='" + admin.getId() + "')].totalSeconds").value(0))
                    .andExpect(jsonPath("$.content[?(@.userId=='" + agent.getId() + "')].role").value("AGENT"))
                    .andExpect(jsonPath("$.content[?(@.userId=='" + agent.getId() + "')].totalSeconds")
                            .value(3600 + 7200 + 7200))
                    .andExpect(jsonPath("$.content[?(@.userId=='" + agent.getId() + "')].days.length()").value(4))
                    .andExpect(jsonPath("$.content[?(@.userId=='" + agent.getId() + "')].days[0].date")
                            .value(RANGE_FROM))
                    .andExpect(jsonPath("$.content[?(@.userId=='" + agent.getId() + "')].days[0].availableSeconds")
                            .value(3600))
                    .andExpect(jsonPath("$.content[?(@.userId=='" + agent.getId() + "')].days[3].availableSeconds")
                            .value(0));
        }

        @Test
        void anotherTenantsIntervalsNeverAppear() throws Exception {
            User admin = newUser();
            Organization acme = acmeWith(admin);
            Organization other = organization("Other");
            Membership foreign = join(other, newUser(), Roles.AGENT);
            closed(foreign, "2026-11-14T08:00:00Z", "2026-11-14T09:00:00Z");

            report(acme, admin, RANGE_FROM, RANGE_TO)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].totalSeconds").value(0));
        }

        private void closed(Membership seat, String start, String end) {
            AvailabilityInterval interval = new AvailabilityInterval(seat, Instant.parse(start));
            interval.close(Instant.parse(end));
            intervals.saveAndFlush(interval);
        }

        @Test
        void anAgentIsForbidden() throws Exception {
            User agent = newUser();
            Organization acme = organization("Acme");
            join(acme, agent, Roles.AGENT);

            report(acme, agent, RANGE_FROM, RANGE_TO).andExpect(status().isForbidden());
        }

        @Test
        void anAdminOfAnotherOrganizationGetsA404() throws Exception {
            User outsider = newUser();
            join(organization("Mine"), outsider, Roles.ADMIN);
            Organization theirs = organization("Theirs");
            join(theirs, newUser(), Roles.ADMIN);

            report(theirs, outsider, RANGE_FROM, RANGE_TO).andExpect(status().isNotFound());
        }

        @Test
        void aReversedRangeIsRejected() throws Exception {
            User admin = newUser();
            report(acmeWith(admin), admin, RANGE_TO, RANGE_FROM).andExpect(status().isBadRequest());
        }

        @Test
        void aRangeOver31DaysIsRejectedButExactly31IsAllowed() throws Exception {
            User admin = newUser();
            Organization acme = acmeWith(admin);

            report(acme, admin, "2026-10-01", "2026-11-01").andExpect(status().isBadRequest());
            report(acme, admin, "2026-10-01", "2026-10-31").andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].days.length()").value(31));
        }

        @Test
        void aMissingOrMalformedDateIsRejected() throws Exception {
            User admin = newUser();
            Organization acme = acmeWith(admin);

            report(acme, admin, null, RANGE_TO).andExpect(status().isBadRequest());
            report(acme, admin, RANGE_FROM, null).andExpect(status().isBadRequest());
            report(acme, admin, "13-11-2026", RANGE_TO).andExpect(status().isBadRequest());
        }

        @Test
        void anAnonymousCallerIsUnauthorized() throws Exception {
            mvc.perform(get(path(organization("Acme"), "/report")).param("from", RANGE_FROM).param("to", RANGE_TO))
                    .andExpect(status().isUnauthorized());
        }
    }
}
