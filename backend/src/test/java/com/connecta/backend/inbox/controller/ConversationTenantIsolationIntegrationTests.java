package com.connecta.backend.inbox.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.connecta.backend.config.JwtProperties;
import com.connecta.backend.inbox.domain.AttachmentKind;
import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.inbox.domain.Conversation;
import com.connecta.backend.inbox.domain.ConversationStatus;
import com.connecta.backend.inbox.domain.Message;
import com.connecta.backend.inbox.domain.MessageAttachment;
import com.connecta.backend.inbox.repository.ConversationParticipantRepository;
import com.connecta.backend.inbox.repository.ConversationRepository;
import com.connecta.backend.inbox.repository.MessageAttachmentRepository;
import com.connecta.backend.inbox.repository.MessageRepository;
import com.connecta.backend.inbox.repository.ParticipantRepository;
import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.org.repository.MembershipRepository;
import com.connecta.backend.org.repository.OrganizationRepository;
import com.connecta.backend.support.InboxFixtures;
import com.connecta.backend.support.RandomJwtSecret;
import com.connecta.backend.support.StubChannelClients;
import com.connecta.backend.support.TestClock;
import com.connecta.backend.support.TestTokens;
import com.connecta.backend.user.domain.Roles;
import com.connecta.backend.user.domain.User;
import com.connecta.backend.user.repository.RoleRepository;
import com.connecta.backend.user.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
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
 * Tenant isolation through the database: real controllers, services and repositories on PostgreSQL, with only
 * Meta stubbed. A member of organization B never reaches organization A's conversation: the list, the detail,
 * the message history, the status change and the send all answer 404, whether B asks under A's organization id
 * (not a member) or under its own id with A's conversation id (not found). Nothing of A's is changed or sent.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false"})
@AutoConfigureMockMvc
@Import({TestClock.class, StubChannelClients.class})
@ContextConfiguration(initializers = RandomJwtSecret.class)
class ConversationTenantIsolationIntegrationTests {

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
    StubChannelClients meta;

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

    @Autowired
    MessageAttachmentRepository attachments;

    private Organization orgA;
    private Organization orgB;
    private User userB;
    private User userA;
    private Conversation conversationOfA;
    private long messagesBefore;

    @BeforeEach
    void twoTenants() {
        meta.reset();
        InboxFixtures fixtures = new InboxFixtures(users, roles, organizations, memberships, participants,
                conversations, links, messages);
        orgA = fixtures.organization("Alpha");
        orgB = fixtures.organization("Beta");
        userA = fixtures.user(Roles.AGENT);
        userB = fixtures.user(Roles.AGENT);
        fixtures.join(orgA, userA, Roles.AGENT);
        fixtures.join(orgB, userB, Roles.AGENT);
        conversationOfA = fixtures.conversation(orgA, Channel.MESSENGER, "Ann", "Secret", TestClock.NOW);
        messagesBefore = messages.count();
    }

    private String asB() {
        return TestTokens.bearer(jwtProperties, userB);
    }

    private String asA() {
        return TestTokens.bearer(jwtProperties, userA);
    }

    private static String conversations(Organization organization) {
        return "/api/organizations/" + organization.getId() + "/conversations";
    }

    private String conversation(Organization organization) {
        return conversations(organization) + "/" + conversationOfA.getId();
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String authorization,
            String body) {
        return request.header(HttpHeaders.AUTHORIZATION, authorization).contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    @Nested
    class Listing {

        @Test
        void bSeesOnlyItsOwnEmptyList() throws Exception {
            mvc.perform(get(conversations(orgB)).header(HttpHeaders.AUTHORIZATION, asB()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(0))
                    .andExpect(jsonPath("$.content").isEmpty());
        }

        @Test
        void bAskingForAsListIsNotFound() throws Exception {
            mvc.perform(get(conversations(orgA)).header(HttpHeaders.AUTHORIZATION, asB()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("Organization not found"));
        }

        @Test
        void aStillSeesItsOwnConversation() throws Exception {
            mvc.perform(get(conversations(orgA)).header(HttpHeaders.AUTHORIZATION,
                    TestTokens.bearer(jwtProperties, userA)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1));
        }
    }

    @Nested
    class Detail {

        @Test
        void underAsOrganizationIdIsNotFound() throws Exception {
            mvc.perform(get(conversation(orgA)).header(HttpHeaders.AUTHORIZATION, asB()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("Organization not found"));
        }

        @Test
        void underItsOwnOrganizationIdWithAsConversationIdIsNotFound() throws Exception {
            mvc.perform(get(conversation(orgB)).header(HttpHeaders.AUTHORIZATION, asB()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("Conversation not found"));
        }

        @Test
        void aStillReadsItsOwnConversation() throws Exception {
            mvc.perform(get(conversation(orgA)).header(HttpHeaders.AUTHORIZATION, asA()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(conversationOfA.getId().toString()));
        }
    }

    @Nested
    class History {

        @Test
        void aStillReadsItsOwnHistory() throws Exception {
            mvc.perform(get(conversation(orgA) + "/messages").header(HttpHeaders.AUTHORIZATION, asA()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray());
        }

        @Test
        void underAsOrganizationIdIsNotFound() throws Exception {
            mvc.perform(get(conversation(orgA) + "/messages").header(HttpHeaders.AUTHORIZATION, asB()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("Organization not found"));
        }

        @Test
        void underItsOwnOrganizationIdWithAsConversationIdIsNotFoundAndLeaksNoMessage() throws Exception {
            mvc.perform(get(conversation(orgB) + "/messages").header(HttpHeaders.AUTHORIZATION, asB()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("Conversation not found"))
                    .andExpect(jsonPath("$.content").doesNotExist());
        }
    }

    @Nested
    class Attachments {

        private Message messageOfA;

        @BeforeEach
        void anAttachmentOnAsMessage() {
            messageOfA = messages.findAll().stream()
                    .filter(message -> message.getConversation().getId().equals(conversationOfA.getId()))
                    .findFirst().orElseThrow();
            attachments.saveAndFlush(new MessageAttachment(messageOfA, AttachmentKind.IMAGE,
                    "https://cdn.example/a.jpg", "a.jpg", "image/jpeg"));
        }

        @Test
        void aReadsItsMessageWithTheAttachment() throws Exception {
            mvc.perform(get(conversation(orgA) + "/messages").header(HttpHeaders.AUTHORIZATION, asA()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].attachments[0].kind").value("IMAGE"))
                    .andExpect(jsonPath("$.content[0].attachments[0].url").value("https://cdn.example/a.jpg"))
                    .andExpect(jsonPath("$.content[0].attachments[0].fileName").value("a.jpg"))
                    .andExpect(jsonPath("$.content[0].attachments[0].contentType").value("image/jpeg"));
        }

        @Test
        void bNeverReachesAsAttachment() throws Exception {
            mvc.perform(get(conversation(orgB) + "/messages").header(HttpHeaders.AUTHORIZATION, asB()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.content").doesNotExist());
        }

        @Test
        void loadingAsMessageIdsUnderBsOrganizationFindsNothing() {
            assertTrue(attachments.findByMessage(orgB.getId(), List.of(messageOfA.getId())).isEmpty());
            assertEquals(1, attachments.findByMessage(orgA.getId(), List.of(messageOfA.getId()))
                    .get(messageOfA.getId()).size());
        }
    }

    @Nested
    class StatusChange {

        @Test
        void aCanStillCloseItsOwnConversation() throws Exception {
            mvc.perform(json(patch(conversation(orgA)), asA(), "{\"status\":\"CLOSED\"}"))
                    .andExpect(status().isOk());

            assertEquals(ConversationStatus.CLOSED, statusOfA());
        }

        @Test
        void underAsOrganizationIdIsNotFoundAndChangesNothing() throws Exception {
            mvc.perform(json(patch(conversation(orgA)), asB(), "{\"status\":\"CLOSED\"}"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("Organization not found"));

            assertEquals(ConversationStatus.OPEN, statusOfA());
        }

        @Test
        void underItsOwnOrganizationIdWithAsConversationIdIsNotFoundAndChangesNothing() throws Exception {
            mvc.perform(json(patch(conversation(orgB)), asB(), "{\"status\":\"CLOSED\"}"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("Conversation not found"));

            assertEquals(ConversationStatus.OPEN, statusOfA());
        }

        private ConversationStatus statusOfA() {
            return conversations.findById(conversationOfA.getId()).orElseThrow().getStatus();
        }
    }

    @Nested
    class Send {

        @Test
        void aStillSendsOnItsOwnConversationExactlyOnce() throws Exception {
            mvc.perform(json(post(conversation(orgA) + "/messages"), asA(), "{\"text\":\"hi\"}"))
                    .andExpect(status().isCreated());

            assertEquals(1, meta.sends());
            assertEquals(messagesBefore + 1, messages.count());
        }

        @Test
        void underAsOrganizationIdIsNotFoundAndNothingIsSent() throws Exception {
            mvc.perform(json(post(conversation(orgA) + "/messages"), asB(), "{\"text\":\"hi\"}"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("Organization not found"));

            assertNothingSentAndStored();
        }

        @Test
        void underItsOwnOrganizationIdWithAsConversationIdIsNotFoundAndNothingIsSent() throws Exception {
            mvc.perform(json(post(conversation(orgB) + "/messages"), asB(), "{\"text\":\"hi\"}"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("Conversation not found"));

            assertNothingSentAndStored();
        }

        private void assertNothingSentAndStored() {
            assertEquals(0, meta.sends());
            assertEquals(messagesBefore, messages.count());
        }
    }
}
