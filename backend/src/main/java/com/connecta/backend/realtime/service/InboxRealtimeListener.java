package com.connecta.backend.realtime.service;

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
}
