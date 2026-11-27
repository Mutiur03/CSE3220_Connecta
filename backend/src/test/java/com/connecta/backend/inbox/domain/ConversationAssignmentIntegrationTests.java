package com.connecta.backend.inbox.domain;

import com.connecta.backend.support.SharedPostgres;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.connecta.backend.inbox.repository.ConversationAssignmentRepository;
import com.connecta.backend.org.domain.Membership;
import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.support.RandomJwtSecret;
import com.connecta.backend.support.TestClock;
import com.connecta.backend.user.domain.User;
import com.connecta.backend.user.repository.RoleRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Real PostgreSQL: the conversation assignee and conversation_assignments match the entities, history is
 * kept when a member leaves, and members of another organization cannot be assigned. Skipped without Docker.
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false"})
@Import(TestClock.class)
@ContextConfiguration(initializers = RandomJwtSecret.class)
class ConversationAssignmentIntegrationTests extends SharedPostgres {

    private static final PageRequest NEWEST_FIRST = PageRequest.of(0, 20, Sort.by("createdAt").descending());

    @Autowired
    EntityManager entities;

    @Autowired
    TransactionTemplate transactions;

    @Autowired
    RoleRepository roles;

    @Autowired
    ConversationAssignmentRepository history;

    @Test
    void storesTheAssigneeAndEveryHandOver() {
        Organization org = newOrganization();
        Membership first = newMember(org);
        Membership second = newMember(org);
        Conversation conversation = newConversation(org);

        assign(conversation, first, AssignmentSource.AUTO_ROUND_ROBIN);
        assign(conversation, second, AssignmentSource.MANUAL);

        List<ConversationAssignment> rows = transactions.execute(status -> {
            List<ConversationAssignment> page = history
                    .findByConversationIdAndOrganizationId(conversation.getId(), org.getId(), NEWEST_FIRST)
                    .getContent();
            assertEquals(second, entities.find(Conversation.class, conversation.getId()).getAssignee());
            return page;
        });

        assertEquals(2, rows.size());
        assertEquals(AssignmentSource.MANUAL, rows.get(0).getSource());
        assertEquals(first, rows.get(0).getFromMember());
        assertEquals(second, rows.get(0).getToMember());
        assertNull(rows.get(1).getFromMember());
    }

    @Test
    void anotherOrganizationCannotReadTheHistory() {
        Organization org = newOrganization();
        Conversation conversation = newConversation(org);
        assign(conversation, newMember(org), AssignmentSource.MANUAL);

        assertTrue(history.findByConversationIdAndOrganizationId(conversation.getId(), newOrganization().getId(),
                NEWEST_FIRST).isEmpty());
    }

    @Test
    void rejectsAnAssigneeFromAnotherOrganization() {
        Conversation conversation = newConversation(newOrganization());
        Membership stranger = newMember(newOrganization());

        assertThrows(PersistenceException.class,
                () -> assign(conversation, stranger, AssignmentSource.MANUAL));
    }

    @Test
    void keepsTheHistoryWhenTheAssigneeLeaves() {
        Organization org = newOrganization();
        Membership member = newMember(org);
        Conversation conversation = newConversation(org);
        assign(conversation, member, AssignmentSource.AUTO_ROUND_ROBIN);

        transactions.executeWithoutResult(status -> entities.remove(entities.find(Membership.class, member.getId())));

        transactions.executeWithoutResult(status -> {
            assertNull(entities.find(Conversation.class, conversation.getId()).getAssignee());
            List<ConversationAssignment> rows = history
                    .findByConversationIdAndOrganizationId(conversation.getId(), org.getId(), NEWEST_FIRST)
                    .getContent();
            assertEquals(1, rows.size());
            assertNull(rows.get(0).getToMember());
        });
    }

    private void assign(Conversation conversation, Membership to, AssignmentSource source) {
        transactions.executeWithoutResult(status -> {
            Conversation managed = entities.find(Conversation.class, conversation.getId());
            entities.persist(managed.assignTo(entities.find(Membership.class, to.getId()), null, source));
            entities.flush();
        });
    }

    private Conversation newConversation(Organization org) {
        return transactions.execute(status -> {
            Conversation conversation = new Conversation(org, Channel.MESSENGER, "thread-" + UUID.randomUUID());
            entities.persist(conversation);
            return conversation;
        });
    }

    private Organization newOrganization() {
        return transactions.execute(status -> {
            Organization org = new Organization("Org " + UUID.randomUUID(), "org-" + UUID.randomUUID());
            entities.persist(org);
            return org;
        });
    }

    private Membership newMember(Organization org) {
        return transactions.execute(status -> {
            User user = new User("u" + UUID.randomUUID() + "@example.com", "hash", "Agent");
            entities.persist(user);
            Membership member = new Membership(org, user, roles.findByName("AGENT").orElseThrow());
            entities.persist(member);
            return member;
        });
    }
}
