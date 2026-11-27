package com.connecta.backend.inbox.service;

import com.connecta.backend.support.SharedPostgres;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.inbox.domain.Conversation;
import com.connecta.backend.inbox.repository.ConversationParticipantRepository;
import com.connecta.backend.inbox.repository.ConversationRepository;
import com.connecta.backend.inbox.repository.MessageRepository;
import com.connecta.backend.inbox.repository.ParticipantRepository;
import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.org.repository.MembershipRepository;
import com.connecta.backend.org.repository.OrganizationRepository;
import com.connecta.backend.support.InboxFixtures;
import com.connecta.backend.support.RandomJwtSecret;
import com.connecta.backend.support.StubChannelClients;
import com.connecta.backend.support.TestClock;
import com.connecta.backend.user.domain.Roles;
import com.connecta.backend.user.domain.User;
import com.connecta.backend.user.repository.RoleRepository;
import com.connecta.backend.user.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * The real Spring proxies around {@link OutgoingMessageService}: the Meta call must run with no database
 * transaction, while the recorder step must run inside one. A mock-based test cannot prove this, because
 * {@code @Transactional} only takes effect through the proxy.
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false"})
@Import({TestClock.class, StubChannelClients.class, OutgoingMessageServiceIntegrationTests.RecorderProbe.class})
@ContextConfiguration(initializers = RandomJwtSecret.class)
class OutgoingMessageServiceIntegrationTests extends SharedPostgres {

    /** Observes the event the recorder publishes while its own transaction is open. */
    @TestConfiguration(proxyBeanMethods = false)
    static class RecorderProbe {

        final List<Boolean> transactionDuringRecord = new ArrayList<>();

        @EventListener
        void onMessage(MessageReceivedEvent event) {
            transactionDuringRecord.add(TransactionSynchronizationManager.isActualTransactionActive());
        }
    }

    @Autowired
    OutgoingMessageService service;

    @Autowired
    StubChannelClients meta;

    @Autowired
    RecorderProbe probe;

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

    @BeforeEach
    void clean() {
        meta.reset();
        probe.transactionDuringRecord.clear();
    }

    @Test
    void metaIsCalledWithoutATransactionAndTheRecorderRunsInsideOne() {
        InboxFixtures fixtures = new InboxFixtures(users, roles, organizations, memberships, participants,
                conversations, links, messages);
        User agent = fixtures.user(Roles.AGENT);
        Organization acme = fixtures.organization("Acme");
        fixtures.join(acme, agent, Roles.AGENT);
        Conversation conversation = fixtures.conversation(acme, Channel.MESSENGER, "Ann", "Hi", TestClock.NOW);

        service.send(agent.getId().toString(), acme.getId(), conversation.getId(), "hello");

        assertEquals(List.of(false), meta.transactionDuringSends());
        assertEquals(List.of(true), probe.transactionDuringRecord);
        assertTrue(messages.findByOrganizationIdAndExternalMessageId(acme.getId(), "mid.stub.1").isPresent());
    }
}
