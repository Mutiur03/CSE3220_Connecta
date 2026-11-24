package com.connecta.backend.realtime.service;

import com.connecta.backend.inbox.service.ParticipantProfileUpdatedEvent;
import com.connecta.backend.realtime.dto.RealtimeEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.ObjectMapper;

/** Observer: pushes {@code participant.updated} to the organization's open sessions after the name is committed. */
@Component
@RequiredArgsConstructor
@Slf4j
public class ParticipantRealtimeListener {

    private final RealtimeSessionRegistry registry;
    private final ObjectMapper json;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onParticipantUpdated(ParticipantProfileUpdatedEvent event) {
        try {
            registry.broadcast(event.organizationId(),
                    json.writeValueAsString(RealtimeEnvelope.participantUpdated(event.conversationId())));
        } catch (RuntimeException e) {
            log.warn("Realtime push failed for conversation {}", event.conversationId(), e);
        }
    }
}
