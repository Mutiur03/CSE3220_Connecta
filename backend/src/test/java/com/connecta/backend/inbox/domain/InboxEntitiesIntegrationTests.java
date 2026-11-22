package com.connecta.backend.inbox.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.org.repository.OrganizationRepository;
import com.connecta.backend.support.RandomJwtSecret;
import com.connecta.backend.support.TestClock;
import com.connecta.backend.user.domain.User;
import com.connecta.backend.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Real PostgreSQL: the inbox tables from V202611221400 match the entities (ddl-auto=validate), the
 * many-to-many join works, and the constraints that protect the data hold. Skipped when Docker is absent.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false"})
@Import(TestClock.class)
@ContextConfiguration(initializers = RandomJwtSecret.class)
class InboxEntitiesIntegrationTests {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    EntityManager entities;

    @Autowired
    TransactionTemplate transactions;

    @Autowired
    OrganizationRepository organizations;

    @Autowired
    UserRepository users;

    @Nested
    class Relationships {

        @Test
        void readsAConversationWithItsParticipantsAndMessages() {
            UUID conversationId = transactions.execute(status -> {
                Organization org = newOrganization();
                Conversation conversation = new Conversation(org, Channel.INSTAGRAM, "thread-1");
                Participant customer = new Participant(org, Channel.INSTAGRAM, "ig-user-1", "Ada");
                entities.persist(conversation);
                entities.persist(customer);
                entities.persist(new ConversationParticipant(conversation, customer));
                entities.persist(Message.inbound(conversation, customer, "mid-1", "Hi", Instant.now()));
                entities.persist(Message.outbound(conversation, newUser(), "mid-2", "Hello", Instant.now()));
                return conversation.getId();
            });

            transactions.executeWithoutResult(status -> {
                List<ConversationParticipant> links = entities.createQuery(
                        "select cp from ConversationParticipant cp where cp.conversation.id = :id",
                        ConversationParticipant.class).setParameter("id", conversationId).getResultList();
                List<Message> messages = entities.createQuery(
                        "select m from Message m where m.conversation.id = :id order by m.direction", Message.class)
                        .setParameter("id", conversationId).getResultList();

                assertEquals(1, links.size());
                assertEquals("Ada", links.get(0).getParticipant().getDisplayName());
                assertEquals(MessageDirection.INBOUND, messages.get(0).getDirection());
                assertEquals(MessageDirection.OUTBOUND, messages.get(1).getDirection());
                assertNotNull(messages.get(0).getCreatedAt());
            });
        }
    }

    @Nested
    class Constraints {

        @Test
        void rejectsTwoConversationsWithTheSameThreadInOneOrganization() {
            Organization org = newOrganization();
            persistInTransaction(new Conversation(org, Channel.MESSENGER, "thread-dup"));

            assertThrows(PersistenceException.class,
                    () -> persistInTransaction(new Conversation(org, Channel.MESSENGER, "thread-dup")));
        }

        @Test
        void rejectsAnInboundMessageWithoutASender() {
            Conversation conversation = persistConversation(newOrganization());

            assertThrows(PersistenceException.class,
                    () -> persistInTransaction(Message.inbound(conversation, null, "mid-x", "Hi", Instant.now())));
        }

        @Test
        void rejectsTheSameMetaMessageTwiceInOneOrganization() {
            Organization org = newOrganization();
            Conversation conversation = persistConversation(org);
            Participant customer = persistParticipant(org, "ig-user-dup");
            persistInTransaction(Message.inbound(conversation, customer, "mid-dup", "Hi", Instant.now()));

            assertThrows(PersistenceException.class,
                    () -> persistInTransaction(Message.inbound(conversation, customer, "mid-dup", "Hi", Instant.now())));
        }

        @Test
        void rejectsALinkBetweenAConversationAndAParticipantOfAnotherOrganization() {
            Conversation conversation = persistConversation(newOrganization());
            Participant otherTenantCustomer = persistParticipant(newOrganization(), "ig-user-other");

            assertThrows(PersistenceException.class,
                    () -> persistInTransaction(new ConversationParticipant(conversation, otherTenantCustomer)));
        }
    }

    private Conversation persistConversation(Organization org) {
        return persistInTransaction(new Conversation(org, Channel.INSTAGRAM, "thread-" + UUID.randomUUID()));
    }

    private Participant persistParticipant(Organization org, String externalId) {
        return persistInTransaction(new Participant(org, Channel.INSTAGRAM, externalId, null));
    }

    private <T> T persistInTransaction(T entity) {
        return transactions.execute(status -> {
            entities.persist(entity);
            entities.flush();
            return entity;
        });
    }

    private Organization newOrganization() {
        return transactions.execute(status -> {
            Organization org = new Organization("Org " + UUID.randomUUID(), "org-" + UUID.randomUUID());
            entities.persist(org);
            return org;
        });
    }

    private User newUser() {
        return users.saveAndFlush(new User("u" + UUID.randomUUID() + "@example.com", "hash", "Agent"));
    }
}
