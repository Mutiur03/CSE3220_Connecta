package com.connecta.backend.inbox.controller;

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
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * GET /api/organizations/{id}/conversations against real PostgreSQL (Flyway runs the migrations): body shape,
 * both channels in one list, filters, paging validation, allowed roles (ADMIN, AGENT), 401, and the 404 for a
 * non-member and for another tenant's conversations. Skipped without Docker.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false"})
@AutoConfigureMockMvc
@Import(TestClock.class)
@ContextConfiguration(initializers = RandomJwtSecret.class)
class ConversationListControllerIntegrationTests {

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

    @Autowired
    ParticipantRepository participants;

    @Autowired
    ConversationRepository conversations;

    @Autowired
    ConversationParticipantRepository links;

    @Autowired
    MessageRepository messages;

    private static String path(Organization organization) {
        return "/api/organizations/" + organization.getId() + "/conversations";
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

    /** A conversation with one customer and one inbound message, last active at the given time. */
    private Conversation conversation(Organization organization, Channel channel, String customer, String text,
            Instant at) {
        Participant participant = participants.saveAndFlush(
                new Participant(organization, channel, "ext-" + UUID.randomUUID(), customer));
        Conversation conversation = new Conversation(organization, channel, "thread-" + UUID.randomUUID());
        conversation.recordMessageAt(at);
        conversation = conversations.saveAndFlush(conversation);
        links.saveAndFlush(new ConversationParticipant(conversation, participant));
        messages.saveAndFlush(Message.inbound(conversation, participant, "m-" + UUID.randomUUID(), text, at));
        return conversation;
    }

    @Nested
    class Listing {

        @Test
        void theSummaryCarriesTheStoredPictureAndNullWhenThereIsNone() throws Exception {
            User agent = newUser();
            Organization acme = organization("Acme");
            join(acme, agent, Roles.AGENT);
            Conversation withPicture = conversation(acme, Channel.MESSENGER, "Bob", "Hi", TestClock.NOW);
            conversation(acme, Channel.INSTAGRAM, "Ann", "Yo", TestClock.NOW.minusSeconds(60));
            Participant bob = participants.findAll().stream().filter(p -> "Bob".equals(p.getDisplayName())
                    && p.getOrganization().getId().equals(acme.getId())).findFirst().orElseThrow();
            bob.applyProfile(null, "https://cdn.example/bob.jpg", TestClock.NOW);
            participants.saveAndFlush(bob);

            mvc.perform(get(path(acme)).header(AUTH, TestTokens.bearer(jwtProperties, agent)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(withPicture.getId().toString()))
                    .andExpect(jsonPath("$.content[0].avatarUrl").value("https://cdn.example/bob.jpg"))
                    .andExpect(jsonPath("$.content[1].avatarUrl").doesNotExist());
        }

        @Test
        void aMemberSeesBothChannelsNewestFirstWithTheSummaryFields() throws Exception {
            User agent = newUser();
            Organization acme = organization("Acme");
            join(acme, agent, Roles.AGENT);
            Conversation older = conversation(acme, Channel.INSTAGRAM, "Ann", "Hi", TestClock.NOW.minusSeconds(60));
            Conversation newer = conversation(acme, Channel.MESSENGER, "Bob", "Price?", TestClock.NOW);

            mvc.perform(get(path(acme)).header(AUTH, TestTokens.bearer(jwtProperties, agent)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(2))
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.size").value(20))
                    .andExpect(jsonPath("$.content[0].id").value(newer.getId().toString()))
                    .andExpect(jsonPath("$.content[0].channel").value("MESSENGER"))
                    .andExpect(jsonPath("$.content[0].status").value("OPEN"))
                    .andExpect(jsonPath("$.content[0].participantName").value("Bob"))
                    .andExpect(jsonPath("$.content[0].lastMessagePreview").value("Price?"))
                    .andExpect(jsonPath("$.content[0].lastMessageAt").exists())
                    .andExpect(jsonPath("$.content[1].id").value(older.getId().toString()));
        }

        @Test
        void anAdminMayList() throws Exception {
            User admin = newUser();
            Organization acme = organization("Acme");
            join(acme, admin, Roles.ADMIN);

            mvc.perform(get(path(acme)).header(AUTH, TestTokens.bearer(jwtProperties, admin)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(0));
        }

        @Test
        void theChannelFilterKeepsOnlyThatChannel() throws Exception {
            User agent = newUser();
            Organization acme = organization("Acme");
            join(acme, agent, Roles.AGENT);
            conversation(acme, Channel.INSTAGRAM, "Ann", "Hi", TestClock.NOW);
            conversation(acme, Channel.MESSENGER, "Bob", "Hello", TestClock.NOW);

            mvc.perform(get(path(acme)).param("channel", "INSTAGRAM")
                            .header(AUTH, TestTokens.bearer(jwtProperties, agent)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].participantName").value("Ann"));
        }

        @Test
        void theStatusFilterKeepsOnlyThatStatus() throws Exception {
            User agent = newUser();
            Organization acme = organization("Acme");
            join(acme, agent, Roles.AGENT);
            conversation(acme, Channel.INSTAGRAM, "Ann", "Hi", TestClock.NOW);
            Conversation done = conversation(acme, Channel.MESSENGER, "Bob", "Thanks", TestClock.NOW);
            done.changeStatus(ConversationStatus.CLOSED);
            conversations.saveAndFlush(done);

            mvc.perform(get(path(acme)).param("status", "CLOSED")
                            .header(AUTH, TestTokens.bearer(jwtProperties, agent)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].participantName").value("Bob"))
                    .andExpect(jsonPath("$.content[0].status").value("CLOSED"));
        }

        @Test
        void theNameFilterMatchesPartOfTheCustomerNameIgnoringCase() throws Exception {
            User agent = newUser();
            Organization acme = organization("Acme");
            join(acme, agent, Roles.AGENT);
            conversation(acme, Channel.INSTAGRAM, "Annabel", "Hi", TestClock.NOW);
            conversation(acme, Channel.MESSENGER, "Bob", "Hello", TestClock.NOW);

            mvc.perform(get(path(acme)).param("q", "NAB")
                            .header(AUTH, TestTokens.bearer(jwtProperties, agent)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].participantName").value("Annabel"));
        }

        @Test
        void sizeLimitsThePageButNotTheTotal() throws Exception {
            User agent = newUser();
            Organization acme = organization("Acme");
            join(acme, agent, Roles.AGENT);
            conversation(acme, Channel.INSTAGRAM, "Ann", "Hi", TestClock.NOW);
            conversation(acme, Channel.MESSENGER, "Bob", "Hello", TestClock.NOW);

            mvc.perform(get(path(acme)).param("size", "1")
                            .header(AUTH, TestTokens.bearer(jwtProperties, agent)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.size").value(1))
                    .andExpect(jsonPath("$.totalElements").value(2));
        }
    }

    @Nested
    class Rejections {

        @Test
        void anUnknownChannelIsBadRequest() throws Exception {
            User agent = newUser();
            Organization acme = organization("Acme");
            join(acme, agent, Roles.AGENT);

            mvc.perform(get(path(acme)).param("channel", "FAX")
                            .header(AUTH, TestTokens.bearer(jwtProperties, agent)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void anUnknownStatusIsBadRequest() throws Exception {
            User agent = newUser();
            Organization acme = organization("Acme");
            join(acme, agent, Roles.AGENT);

            mvc.perform(get(path(acme)).param("status", "SNOOZED")
                            .header(AUTH, TestTokens.bearer(jwtProperties, agent)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void aSizeAboveTheMaximumIsBadRequest() throws Exception {
            User agent = newUser();
            Organization acme = organization("Acme");
            join(acme, agent, Roles.AGENT);

            mvc.perform(get(path(acme)).param("size", "101")
                            .header(AUTH, TestTokens.bearer(jwtProperties, agent)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void aSizeOfZeroIsBadRequest() throws Exception {
            User agent = newUser();
            Organization acme = organization("Acme");
            join(acme, agent, Roles.AGENT);

            mvc.perform(get(path(acme)).param("size", "0")
                            .header(AUTH, TestTokens.bearer(jwtProperties, agent)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void anAnonymousCallerIsUnauthorized() throws Exception {
            Organization acme = organization("Acme");

            mvc.perform(get(path(acme))).andExpect(status().isUnauthorized());
        }

        @Test
        void aNonMemberGetsA404() throws Exception {
            User outsider = newUser();
            Organization acme = organization("Acme");

            mvc.perform(get(path(acme)).header(AUTH, TestTokens.bearer(jwtProperties, outsider)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void anotherTenantsConversationsAreNeverListed() throws Exception {
            User agent = newUser();
            Organization acme = organization("Acme");
            Organization globex = organization("Globex");
            join(acme, agent, Roles.AGENT);
            conversation(globex, Channel.INSTAGRAM, "Secret", "Hidden", TestClock.NOW);

            mvc.perform(get(path(acme)).header(AUTH, TestTokens.bearer(jwtProperties, agent)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(0));
            mvc.perform(get(path(globex)).header(AUTH, TestTokens.bearer(jwtProperties, agent)))
                    .andExpect(status().isNotFound());
        }
    }
}
