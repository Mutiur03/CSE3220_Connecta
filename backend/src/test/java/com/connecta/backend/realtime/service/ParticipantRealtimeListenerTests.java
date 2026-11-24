package com.connecta.backend.realtime.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.connecta.backend.inbox.service.ParticipantProfileUpdatedEvent;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class ParticipantRealtimeListenerTests {

    private final UUID orgId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();
    private final ParticipantProfileUpdatedEvent event = new ParticipantProfileUpdatedEvent(orgId, conversationId);

    @Mock
    RealtimeSessionRegistry registry;

    private ParticipantRealtimeListener listener() {
        return new ParticipantRealtimeListener(registry, JsonMapper.builder().build());
    }

    @Test
    void broadcastsParticipantUpdatedToTheEventsOrganization() {
        listener().onParticipantUpdated(event);

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(registry).broadcast(eq(orgId), json.capture());
        assertThat(json.getValue()).contains("\"type\":\"participant.updated\"",
                "\"conversationId\":\"" + conversationId + "\"");
    }

    @Test
    void neverThrowsWhenThePushFails() {
        doThrow(new IllegalStateException("socket")).when(registry).broadcast(eq(orgId), any());

        assertThatCode(() -> listener().onParticipantUpdated(event)).doesNotThrowAnyException();
    }
}
