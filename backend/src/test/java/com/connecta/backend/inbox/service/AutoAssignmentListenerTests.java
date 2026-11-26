package com.connecta.backend.inbox.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.inbox.domain.MessageDirection;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AutoAssignmentListenerTests {

    private final UUID orgId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();
    private final AutoAssignmentService service = mock(AutoAssignmentService.class);
    private final AutoAssignmentListener listener = new AutoAssignmentListener(service);

    private MessageReceivedEvent event(MessageDirection direction, boolean isNew) {
        return new MessageReceivedEvent(orgId, conversationId, UUID.randomUUID(), Channel.MESSENGER, direction,
                isNew);
    }

    @Test
    void aNewInboundConversationIsAssigned() {
        listener.onMessageReceived(event(MessageDirection.INBOUND, true));

        verify(service).assignIfUnassigned(orgId, conversationId);
    }

    @Test
    void anExistingConversationIsIgnored() {
        listener.onMessageReceived(event(MessageDirection.INBOUND, false));

        verifyNoInteractions(service);
    }

    @Test
    void anOutboundMessageIsIgnored() {
        listener.onMessageReceived(event(MessageDirection.OUTBOUND, true));

        verifyNoInteractions(service);
    }

    @Test
    void aFailureNeverReachesMessageIntake() {
        doThrow(new IllegalStateException("db down")).when(service).assignIfUnassigned(orgId, conversationId);

        assertDoesNotThrow(() -> listener.onMessageReceived(event(MessageDirection.INBOUND, true)));
    }
}
