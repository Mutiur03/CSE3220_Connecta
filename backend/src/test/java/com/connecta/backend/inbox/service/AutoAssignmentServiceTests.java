package com.connecta.backend.inbox.service;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.connecta.backend.inbox.domain.AssignmentSource;
import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.inbox.domain.Conversation;
import com.connecta.backend.inbox.domain.ConversationAssignment;
import com.connecta.backend.inbox.repository.ConversationAssignmentRepository;
import com.connecta.backend.inbox.repository.ConversationRepository;
import com.connecta.backend.org.domain.Membership;
import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.user.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AutoAssignmentServiceTests {

    private final UUID orgId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();
    private final Organization org = new Organization("Org", "org");
    private final Membership agent = new Membership(org, new User("a@example.com", "hash", "A"), null);
    private final ConversationRepository conversations = mock(ConversationRepository.class);
    private final ConversationAssignmentRepository history = mock(ConversationAssignmentRepository.class);
    private final AssignmentStrategy strategy = mock(AssignmentStrategy.class);
    private final AssignmentStrategyFactory factory = mock(AssignmentStrategyFactory.class);
    private AutoAssignmentService service;
    private Conversation conversation;

    @BeforeEach
    void setUp() {
        conversation = new Conversation(org, Channel.INSTAGRAM, "thread");
        when(conversations.findByIdAndOrganizationId(conversationId, orgId)).thenReturn(Optional.of(conversation));
        when(factory.forOrganization(orgId)).thenReturn(strategy);
        service = new AutoAssignmentService(conversations, history, factory);
    }

    @Nested
    class Unassigned {

        @Test
        void givesTheConversationToTheChosenAgentAndSavesTheHistoryRow() {
            when(strategy.choose(conversation)).thenReturn(Optional.of(agent));

            service.assignIfUnassigned(orgId, conversationId);

            assertSame(agent, conversation.getAssignee());
            ArgumentCaptor<ConversationAssignment> saved = ArgumentCaptor.forClass(ConversationAssignment.class);
            verify(history).save(saved.capture());
            assertSame(AssignmentSource.AUTO_ROUND_ROBIN, saved.getValue().getSource());
            assertNull(saved.getValue().getAssignedBy());
        }

        @Test
        void staysUnassignedWhenNobodyIsAvailable() {
            when(strategy.choose(conversation)).thenReturn(Optional.empty());

            service.assignIfUnassigned(orgId, conversationId);

            assertNull(conversation.getAssignee());
            verify(history, never()).save(any());
        }
    }

    @Test
    void neverReassignsAConversationThatHasAnAssignee() {
        conversation.assignTo(agent, null, AssignmentSource.AUTO_ROUND_ROBIN);

        service.assignIfUnassigned(orgId, conversationId);

        verifyNoInteractions(strategy, history);
    }

    @Test
    void anUnknownConversationThrows() {
        assertThrows(ConversationNotFoundException.class, () -> service.assignIfUnassigned(orgId, UUID.randomUUID()));
    }
}
