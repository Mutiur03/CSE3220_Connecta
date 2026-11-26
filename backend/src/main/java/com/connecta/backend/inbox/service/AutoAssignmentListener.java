package com.connecta.backend.inbox.service;

import com.connecta.backend.inbox.domain.MessageDirection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Observer: when an inbound message starts a conversation and it is committed, assigns the conversation.
 * A failure is logged and never reaches message intake.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutoAssignmentListener {

    private final AutoAssignmentService assignment;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageReceived(MessageReceivedEvent event) {
        if (!event.newConversation() || event.direction() != MessageDirection.INBOUND) {
            return;
        }
        try {
            assignment.assignIfUnassigned(event.organizationId(), event.conversationId());
        } catch (RuntimeException e) {
            log.warn("Assigning conversation {} failed: {}", event.conversationId(), e.getClass().getSimpleName());
        }
    }
}
