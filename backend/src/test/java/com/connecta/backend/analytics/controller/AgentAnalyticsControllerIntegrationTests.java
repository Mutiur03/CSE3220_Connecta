package com.connecta.backend.analytics.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.connecta.backend.config.JwtProperties;
import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.inbox.domain.Conversation;
import com.connecta.backend.inbox.domain.ConversationStatus;
import com.connecta.backend.inbox.domain.Message;
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
 * Agent analytics end to end on PostgreSQL. Organization A has an admin and an agent; the agent answered one
 * conversation after 600 s and closed it, all inside 1-7 November (UTC). Organization B is the other tenant.
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false"})
@AutoConfigureMockMvc
@Import({TestClock.class, StubChannelClients.class})
@ContextConfiguration(initializers = RandomJwtSecret.class)
class AgentAnalyticsControllerIntegrationTests extends SharedPostgres {

    private static final String RANGE = "?from=2026-11-01&to=2026-11-07";
    private static final String AGENT_ROW = "$.content[?(@.role=='AGENT')]";

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
    private User adminA;
    private User agentA;
    private User adminB;

    @BeforeEach
    void seed() {
        fixtures = new InboxFixtures(users, roles, organizations, memberships, participants, conversations, links,
                messages);
        orgA = fixtures.organization("Alpha");
        Organization orgB = fixtures.organization("Beta");
        adminA = fixtures.user(Roles.ADMIN);
        agentA = fixtures.user(Roles.AGENT);
        adminB = fixtures.user(Roles.ADMIN);
        fixtures.join(orgA, adminA, Roles.ADMIN);
        fixtures.join(orgA, agentA, Roles.AGENT);
        fixtures.join(orgB, adminB, Roles.ADMIN);

        Instant asked = Instant.parse("2026-11-02T10:00:00Z");
        Conversation chat = fixtures.conversation(orgA, Channel.MESSENGER, "Ann", "Hi", asked);
        messages.saveAndFlush(Message.outbound(chat, agentA, "m-" + UUID.randomUUID(), "Hello",
                asked.plusSeconds(600)));
        chat.changeStatus(ConversationStatus.CLOSED, agentA, asked.plusSeconds(3600));
        conversations.saveAndFlush(chat);
    }

    private ResultActions report(User user, String query) throws Exception {
        return mvc.perform(get("/api/organizations/" + orgA.getId() + "/analytics/agents" + query)
                .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(jwtProperties, user)));
    }

    @Nested
    class Report {

        @Test
        void listsEveryMemberWithTheirNumbers() throws Exception {
            report(adminA, RANGE + "&zone=UTC")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.from").value("2026-11-01"))
                    .andExpect(jsonPath("$.to").value("2026-11-07"))
                    .andExpect(jsonPath("$.zone").value("UTC"))
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.size").value(20))
                    .andExpect(jsonPath("$.totalElements").value(2))
                    .andExpect(jsonPath("$.content", hasSize(2)))
                    .andExpect(jsonPath(AGENT_ROW + ".resolved").value(1))
                    .andExpect(jsonPath(AGENT_ROW + ".repliesSent").value(1))
                    .andExpect(jsonPath(AGENT_ROW + ".medianFirstResponseSeconds").value(600))
                    .andExpect(jsonPath(AGENT_ROW + ".openNow").value(0));
        }

        @Test
        void pagesWithTheRequestedSize() throws Exception {
            report(adminA, RANGE + "&page=1&size=1")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.page").value(1))
                    .andExpect(jsonPath("$.size").value(1))
                    .andExpect(jsonPath("$.totalElements").value(2))
                    .andExpect(jsonPath("$.content", hasSize(1)));
        }
    }

    @Nested
    class BadRequest {

        @Test
        void fromAfterToIsRefused() throws Exception {
            report(adminA, "?from=2026-11-07&to=2026-11-01")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.detail").value("'from' must not be after 'to'"));
        }

        @Test
        void anUnknownZoneIsRefused() throws Exception {
            report(adminA, "?zone=Mars/Olympus").andExpect(status().isBadRequest());
        }

        @Test
        void aSizeOverTheMaximumIsRefused() throws Exception {
            report(adminA, "?size=101").andExpect(status().isBadRequest());
        }

        @Test
        void aNegativePageIsRefused() throws Exception {
            report(adminA, "?page=-1").andExpect(status().isBadRequest());
        }
    }

    @Nested
    class Access {

        @Test
        void requiresAuthentication() throws Exception {
            mvc.perform(get("/api/organizations/" + orgA.getId() + "/analytics/agents"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void anAgentIsForbidden() throws Exception {
            report(agentA, RANGE).andExpect(status().isForbidden());
        }

        @Test
        void anotherOrganizationsAdminIsNotFound() throws Exception {
            report(adminB, RANGE).andExpect(status().isNotFound());
        }
    }
}
