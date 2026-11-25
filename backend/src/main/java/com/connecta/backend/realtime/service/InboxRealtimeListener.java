package com.connecta.backend.realtime.service;

import com.connecta.backend.inbox.service.AttachmentMirroredEvent;
import com.connecta.backend.inbox.service.HistoryImportedEvent;
import com.connecta.backend.inbox.service.MessageLookupService;
import com.connecta.backend.inbox.service.MessageReceivedEvent;
import com.connecta.backend.realtime.dto.RealtimeEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.ObjectMapper;

/**
 * Observer: reacts to {@link MessageReceivedEvent} after the message is committed and pushes it to the
 * organization's open sessions. It never throws, so a push problem cannot affect the publisher.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InboxRealtimeListener {

    private final MessageLookupService messages;
    private final RealtimeSessionRegistry registry;
    private final ObjectMapper json;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onMessageReceived(MessageReceivedEvent event) {
        try {
            messages.find(event.organizationId(), event.messageId()).ifPresent(message -> registry.broadcast(
                    event.organizationId(), json.writeValueAsString(RealtimeEnvelope.messageCreated(event, message))));
        } catch (RuntimeException e) {
            log.warn("Realtime push failed for message {}", event.messageId(), e);
        }
    }

    /** One refresh push after a history import, instead of one push per imported message. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onHistoryImported(HistoryImportedEvent event) {
        try {
            registry.broadcast(event.organizationId(), json.writeValueAsString(RealtimeEnvelope.inboxRefresh()));
        } catch (RuntimeException e) {
            log.warn("Realtime refresh push failed", e);
        }
    }

    /** Our copy of an attachment is stored: open threads of that conversation reload and show the copy. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAttachmentMirrored(AttachmentMirroredEvent event) {
        try {
            registry.broadcast(event.organizationId(),
                    json.writeValueAsString(RealtimeEnvelope.conversationChanged(event.conversationId())));
        } catch (RuntimeException e) {
            log.warn("Realtime push failed for conversation {}", event.conversationId(), e);
        }
    }
}
