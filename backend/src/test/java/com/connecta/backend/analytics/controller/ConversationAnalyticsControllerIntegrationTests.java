package com.connecta.backend.analytics.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.connecta.backend.config.JwtProperties;
import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.inbox.domain.Conversation;
import com.connecta.backend.inbox.domain.ConversationParticipant;
import com.connecta.backend.inbox.domain.ConversationStatus;
import com.connecta.backend.inbox.domain.Message;
import com.connecta.backend.inbox.domain.Participant;
import com.connecta.backend.inbox.repository.ConversationParticipantRepository;
import com.connecta.backend.inbox.repository.ConversationRepository;
import com.connecta.backend.inbox.repository.MessageRepository;
import com.connecta.backend.inbox.repository.ParticipantRepository;
import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.org.repository.MembershipRepository;
import com.connecta.backend.org.repository.OrganizationRepository;
import com.connecta.backend.support.InboxFixtures;
import com.connecta.backend.support.RandomJwtSecret;
import com.connecta.backend.support.SharedPostgres;
import com.connecta.backend.support.StubChannelClients;
import com.connecta.backend.support.TestClock;
import com.connecta.backend.support.TestTokens;
import com.connecta.backend.user.domain.Roles;
import com.connecta.backend.user.domain.User;
import com.connecta.backend.user.repository.RoleRepository;
import com.connecta.backend.user.repository.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Conversation analytics end to end on PostgreSQL. The range 10-12 November in Asia/Dhaka (UTC+6) runs from
 * 2026-11-09T18:00Z to 2026-11-12T18:00Z, so several messages sit on a different day in Dhaka than in UTC.
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false"})
@AutoConfigureMockMvc
@Import({TestClock.class, StubChannelClients.class})
@ContextConfiguration(initializers = RandomJwtSecret.class)
class ConversationAnalyticsControllerIntegrationTests extends SharedPostgres {

    private static final String DHAKA_RANGE = "?from=2026-11-10&to=2026-11-12&zone=Asia/Dhaka";

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
    ParticipantRepository participants;

    @Autowired
    ConversationRepository conversations;

    @Autowired
    ConversationParticipantRepository links;

    @Autowired
    MessageRepository messages;

    private InboxFixtures fixtures;
    private Organization orgA;
    private Organization orgB;
    private User adminA;
    private User adminB;

    @BeforeEach
    void seed() {
        fixtures = new InboxFixtures(users, roles, organizations, memberships, participants, conversations, links,
                messages);
        orgA = fixtures.organization("Alpha");
        orgB = fixtures.organization("Beta");
        adminA = fixtures.user(Roles.ADMIN);
        adminB = fixtures.user(Roles.ADMIN);
        fixtures.join(orgA, adminA, Roles.ADMIN);
        fixtures.join(orgB, adminB, Roles.ADMIN);

        // 10 Nov 00:30 in Dhaka (still 9 Nov in UTC), answered after 600 s.
        Chat c1 = thread(orgA, Channel.MESSENGER);
        c1.inbound("2026-11-09T18:30:00Z").outbound("2026-11-09T18:40:00Z");
        // 12 Nov 01:00 in Dhaka (11 Nov in UTC), answered after 120 s, then a second reply.
        Chat c2 = thread(orgA, Channel.INSTAGRAM);
        c2.inbound("2026-11-11T19:00:00Z").outbound("2026-11-11T19:02:00Z").outbound("2026-11-11T19:30:00Z");
        // 11 Nov, answered after 1800 s.
        thread(orgA, Channel.INSTAGRAM).inbound("2026-11-11T05:00:00Z").outbound("2026-11-11T05:30:00Z");
        // 11 Nov, never answered, closed.
        Chat c4 = thread(orgA, Channel.MESSENGER).inbound("2026-11-11T06:00:00Z");
        c4.conversation.changeStatus(ConversationStatus.CLOSED, adminA, TestClock.NOW);
        conversations.saveAndFlush(c4.conversation);
        // Started before the range: its message in the range counts as received, not as started.
        thread(orgA, Channel.MESSENGER).inbound("2026-11-01T10:00:00Z").inbound("2026-11-10T10:00:00Z");
        // 13 Nov 00:00 in Dhaka: the exclusive end, so outside the range.
        thread(orgA, Channel.INSTAGRAM).inbound("2026-11-12T18:00:00Z");
        // Another organization inside the range: never counted for A.
        thread(orgB, Channel.INSTAGRAM).inbound("2026-11-11T05:00:00Z").outbound("2026-11-11T05:01:00Z");
    }

    private Chat thread(Organization organization, Channel channel) {
        Participant customer = participants.saveAndFlush(
                new Participant(organization, channel, "ext-" + UUID.randomUUID(), "Customer"));
        Conversation conversation = conversations.saveAndFlush(
                new Conversation(organization, channel, "thread-" + UUID.randomUUID()));
        links.saveAndFlush(new ConversationParticipant(conversation, customer));
        return new Chat(conversation, customer);
    }

    /** One conversation with its customer, to add messages in order. */
    private final class Chat {

        private final Conversation conversation;
        private final Participant customer;

        Chat(Conversation conversation, Participant customer) {
            this.conversation = conversation;
            this.customer = customer;
        }

        Chat inbound(String at) {
            messages.saveAndFlush(Message.inbound(conversation, customer, "m-" + UUID.randomUUID(), "Hi",
                    Instant.parse(at)));
            return this;
        }

        Chat outbound(String at) {
            messages.saveAndFlush(Message.outbound(conversation, adminA, "m-" + UUID.randomUUID(), "Hello",
                    Instant.parse(at)));
            return this;
        }
    }

    private ResultActions report(Organization organization, User user, String query) throws Exception {
        return mvc.perform(get("/api/organizations/" + organization.getId() + "/analytics/conversations" + query)
                .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(jwtProperties, user)));
    }

    @Nested
    class Report {

        @Test
        void countsTheOrganizationsConversationsByDayInTheZone() throws Exception {
            report(orgA, adminA, DHAKA_RANGE)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.from").value("2026-11-10"))
                    .andExpect(jsonPath("$.to").value("2026-11-12"))
                    .andExpect(jsonPath("$.zone").value("Asia/Dhaka"))
                    .andExpect(jsonPath("$.started").value(4))
                    .andExpect(jsonPath("$.startedPerDay", hasSize(3)))
                    .andExpect(jsonPath("$.startedPerDay[0].date").value("2026-11-10"))
                    .andExpect(jsonPath("$.startedPerDay[0].count").value(1))
                    .andExpect(jsonPath("$.startedPerDay[1].date").value("2026-11-11"))
                    .andExpect(jsonPath("$.startedPerDay[1].count").value(2))
                    .andExpect(jsonPath("$.startedPerDay[2].date").value("2026-11-12"))
                    .andExpect(jsonPath("$.startedPerDay[2].count").value(1))
                    .andExpect(jsonPath("$.startedPerChannel[0].channel").value("INSTAGRAM"))
                    .andExpect(jsonPath("$.startedPerChannel[0].count").value(2))
                    .andExpect(jsonPath("$.startedPerChannel[1].channel").value("MESSENGER"))
                    .andExpect(jsonPath("$.startedPerChannel[1].count").value(2))
                    .andExpect(jsonPath("$.statusNow.open").value(5))
                    .andExpect(jsonPath("$.statusNow.closed").value(1))
                    .andExpect(jsonPath("$.messagesReceived").value(5))
                    .andExpect(jsonPath("$.messagesSent").value(4))
                    .andExpect(jsonPath("$.medianFirstResponseSeconds").value(600))
                    .andExpect(jsonPath("$.unanswered").value(1));
        }

        @Test
        void theSameMessagesBucketByUtcDaysWithoutAZone() throws Exception {
            report(orgA, adminA, "?from=2026-11-09&to=2026-11-11")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.zone").value("UTC"))
                    .andExpect(jsonPath("$.startedPerDay[0].count").value(1))
                    .andExpect(jsonPath("$.startedPerDay[1].count").value(0))
                    .andExpect(jsonPath("$.startedPerDay[2].count").value(3));
        }

        @Test
        void anEmptyRangeHasEveryDayAtZeroAndNoMedian() throws Exception {
            report(orgA, adminA, "?from=2026-10-01&to=2026-10-03")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.started").value(0))
                    .andExpect(jsonPath("$.startedPerDay", hasSize(3)))
                    .andExpect(jsonPath("$.startedPerDay[2].count").value(0))
                    .andExpect(jsonPath("$.startedPerChannel", hasSize(2)))
                    .andExpect(jsonPath("$.medianFirstResponseSeconds").value(nullValue()))
                    .andExpect(jsonPath("$.unanswered").value(0));
        }

        @Test
        void defaultsToTheLastThirtyDaysInUtc() throws Exception {
            report(orgA, adminA, "")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.from").value("2026-10-17"))
                    .andExpect(jsonPath("$.to").value("2026-11-15"))
                    .andExpect(jsonPath("$.zone").value("UTC"))
                    .andExpect(jsonPath("$.startedPerDay", hasSize(30)));
        }
    }

    @Nested
    class InvalidRange {

        @Test
        void fromAfterToIsBadRequest() throws Exception {
            report(orgA, adminA, "?from=2026-11-12&to=2026-11-10")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.detail").value("'from' must not be after 'to'"));
        }

        @Test
        void moreThan366DaysIsBadRequest() throws Exception {
            report(orgA, adminA, "?from=2025-01-01&to=2026-01-02")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        void anUnknownZoneIsBadRequest() throws Exception {
            report(orgA, adminA, "?zone=Mars/Olympus")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        void aMalformedDateIsBadRequest() throws Exception {
            report(orgA, adminA, "?from=10-11-2026").andExpect(status().isBadRequest());
        }
    }

    @Nested
    class Access {

        @Test
        void requiresAuthentication() throws Exception {
            mvc.perform(get("/api/organizations/" + orgA.getId() + "/analytics/conversations"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void anAgentIsForbidden() throws Exception {
            User agent = fixtures.user(Roles.AGENT);
            fixtures.join(orgA, agent, Roles.AGENT);

            report(orgA, agent, DHAKA_RANGE).andExpect(status().isForbidden());
        }

        @Test
        void anAdminWhoIsOnlyAnAgentOfTheOrganizationIsForbidden() throws Exception {
            User admin = fixtures.user(Roles.ADMIN);
            fixtures.join(orgA, admin, Roles.AGENT);

            report(orgA, admin, DHAKA_RANGE).andExpect(status().isForbidden());
        }

        @Test
        void anotherOrganizationIsNotFound() throws Exception {
            report(orgA, adminB, DHAKA_RANGE).andExpect(status().isNotFound());
        }

        @Test
        void anotherOrganizationsAdminSeesOnlyItsOwnData() throws Exception {
            report(orgB, adminB, DHAKA_RANGE)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.started").value(1))
                    .andExpect(jsonPath("$.messagesReceived").value(1))
                    .andExpect(jsonPath("$.medianFirstResponseSeconds").value(60));
        }
    }
}
