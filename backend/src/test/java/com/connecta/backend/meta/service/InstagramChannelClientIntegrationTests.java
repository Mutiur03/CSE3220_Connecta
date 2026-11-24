package com.connecta.backend.meta.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.connecta.backend.config.MetaProperties;
import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.inbox.domain.Conversation;
import com.connecta.backend.inbox.dto.MessageResponse;
import com.connecta.backend.inbox.repository.ConversationParticipantRepository;
import com.connecta.backend.inbox.repository.ConversationRepository;
import com.connecta.backend.inbox.repository.MessageRepository;
import com.connecta.backend.inbox.repository.ParticipantRepository;
import com.connecta.backend.inbox.service.ConversationDetailService;
import com.connecta.backend.inbox.service.OutgoingMessageService;
import com.connecta.backend.meta.domain.MetaConnectedAccount;
import com.connecta.backend.meta.repository.MetaConnectedAccountRepository;
import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.org.repository.MembershipRepository;
import com.connecta.backend.org.repository.OrganizationRepository;
import com.connecta.backend.support.InboxFixtures;
import com.connecta.backend.support.RandomJwtSecret;
import com.connecta.backend.support.TestClock;
import com.connecta.backend.user.domain.Roles;
import com.connecta.backend.user.domain.User;
import com.connecta.backend.user.repository.RoleRepository;
import com.connecta.backend.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Sends a reply for a conversation linked to a connected account, against real PostgreSQL. After the
 * transaction that loaded it, the conversation is detached and its account is an uninitialized lazy proxy, as in
 * production (the Meta call runs outside a transaction). The send must not throw LazyInitializationException and
 * must use the linked account's token, not the organization's other account. Only Meta's HTTP boundary is stubbed.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false"})
@Import({TestClock.class, InstagramChannelClientIntegrationTests.MetaHttpStub.class})
@ContextConfiguration(initializers = RandomJwtSecret.class)
class InstagramChannelClientIntegrationTests {

    private static final String SEND_URL = "https://graph.instagram.com/v27.0/me/messages";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    /** The real Instagram adapter and factory, with Meta's HTTP endpoint replaced by a mock server. */
    @TestConfiguration(proxyBeanMethods = false)
    static class MetaHttpStub {

        final RestClient.Builder builder = RestClient.builder();
        final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        @Bean
        @Primary
        ChannelClientFactory instagramOnlyFactory(MetaConnectedAccountRepository accounts, TokenCipher cipher,
                MetaProperties properties) {
            return new ChannelClientFactory(List.of(new InstagramChannelClient(accounts, cipher, properties, builder)));
        }
    }

    @Autowired
    MetaHttpStub meta;

    @Autowired
    OutgoingMessageService service;

    @Autowired
    ConversationDetailService details;

    @Autowired
    MetaConnectedAccountRepository accounts;

    @Autowired
    TokenCipher cipher;

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
    private User agent;
    private Organization acme;

    @BeforeEach
    void setUp() {
        meta.server.reset();
        fixtures = new InboxFixtures(users, roles, organizations, memberships, participants, conversations, links,
                messages);
        agent = fixtures.user(Roles.AGENT);
        acme = fixtures.organization("Acme");
        fixtures.join(acme, agent, Roles.AGENT);
    }

    private MetaConnectedAccount account(String externalId, String token) {
        MetaConnectedAccount account = new MetaConnectedAccount(acme, MetaConnectedAccount.PLATFORM_INSTAGRAM,
                externalId);
        account.refresh(externalId, cipher.encrypt(token), agent, Instant.EPOCH);
        return accounts.saveAndFlush(account);
    }

    private Conversation linkedTo(MetaConnectedAccount account) {
        Conversation conversation = fixtures.conversation(acme, Channel.INSTAGRAM, "Ann", "Hi", TestClock.NOW);
        conversation.linkToInstagramAccount(account);
        return conversations.saveAndFlush(conversation);
    }

    private void metaAccepts(String token, String messageId) {
        meta.server.expect(requestTo(SEND_URL)).andExpect(header("Authorization", "Bearer " + token))
                .andRespond(withSuccess("{\"message_id\":\"" + messageId + "\"}", MediaType.APPLICATION_JSON));
    }

    @Test
    void theConversationIsDetachedWithALazyAccountAfterItsTransactionEnds() {
        Conversation conversation = linkedTo(account("ig-1", "first-token"));

        Conversation loaded = details.find(agent.getId().toString(), acme.getId(), conversation.getId());

        assertFalse(Hibernate.isInitialized(loaded.getInstagramAccount()));
    }

    @Test
    void aReplyUsesTheLinkedAccountsTokenNotTheOrganizationsOtherAccount() {
        account("ig-1", "first-token");
        Conversation conversation = linkedTo(account("ig-2", "second-token"));
        metaAccepts("second-token", "mid.linked");

        MessageResponse sent = service.send(agent.getId().toString(), acme.getId(), conversation.getId(), "hello");

        meta.server.verify();
        assertEquals("hello", sent.text());
        assertEquals(agent.getId(), sent.senderId());
        assertEquals(1, messages.findByOrganizationIdAndExternalMessageId(acme.getId(), "mid.linked").stream()
                .count());
    }

    @Test
    void anUnlinkedConversationOfAnOrganizationWithTwoAccountsIsRefusedAndSendsNothing() {
        account("ig-1", "first-token");
        account("ig-2", "second-token");
        Conversation unlinked = fixtures.conversation(acme, Channel.INSTAGRAM, "Bob", "Hi", TestClock.NOW);

        assertThrows(ConversationNotLinkedException.class,
                () -> service.send(agent.getId().toString(), acme.getId(), unlinked.getId(), "hello"));

        meta.server.verify();
    }
}
