package com.connecta.backend.realtime.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.inbox.domain.MessageDirection;
import com.connecta.backend.inbox.dto.MessageResponse;
import com.connecta.backend.inbox.service.MessageLookupService;
import com.connecta.backend.inbox.service.MessageReceivedEvent;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class InboxRealtimeListenerTests {

    private final UUID orgId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();
    private final UUID messageId = UUID.randomUUID();
    private final MessageReceivedEvent event = new MessageReceivedEvent(orgId, conversationId, messageId,
            Channel.MESSENGER, MessageDirection.INBOUND, true);
    private final MessageResponse message = new MessageResponse(messageId, MessageDirection.INBOUND, "hello",
            Instant.parse("2026-11-24T10:00:00Z"), UUID.randomUUID(), "Rina", List.of());

    @Mock
    MessageLookupService messages;

    @Mock
    RealtimeSessionRegistry registry;

    private InboxRealtimeListener listener() {
        return new InboxRealtimeListener(messages, registry, JsonMapper.builder().build());
    }

    @Nested
    class OnMessageReceived {

        @Test
        void broadcastsTheEnvelopeToTheEventsOrganization() {
            when(messages.find(orgId, messageId)).thenReturn(Optional.of(message));

            listener().onMessageReceived(event);

            ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
            verify(registry).broadcast(eq(orgId), json.capture());
            assertThat(json.getValue()).contains("\"type\":\"message.created\"",
                    "\"conversationId\":\"" + conversationId + "\"", "\"messageId\":\"" + messageId + "\"",
                    "\"channel\":\"MESSENGER\"", "\"direction\":\"INBOUND\"", "\"newConversation\":true",
                    "\"text\":\"hello\"", "\"senderName\":\"Rina\"", "\"sentAt\":\"2026-11-24T10:00:00Z\"");
        }

        @Test
        void aMissingMessageSendsNothing() {
            when(messages.find(orgId, messageId)).thenReturn(Optional.empty());

            listener().onMessageReceived(event);

            verify(registry, never()).broadcast(any(), any());
        }

        @Test
        void aFailureIsSwallowedSoThePublisherIsNotAffected() {
            when(messages.find(orgId, messageId)).thenThrow(new IllegalStateException("db down"));

            assertThatCode(() -> listener().onMessageReceived(event)).doesNotThrowAnyException();
            verify(registry, never()).broadcast(any(), any());
        }

        @Test
        void aBroadcastFailureIsSwallowedToo() {
            when(messages.find(orgId, messageId)).thenReturn(Optional.of(message));
            doThrow(new IllegalStateException("boom")).when(registry).broadcast(any(), any());

            assertThatCode(() -> listener().onMessageReceived(event)).doesNotThrowAnyException();
        }
    }

    @Nested
    class OnHistoryImported {

        @Test
        void broadcastsOneRefreshToTheOrganization() {
            listener().onHistoryImported(new com.connecta.backend.inbox.service.HistoryImportedEvent(orgId));

            ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
            verify(registry).broadcast(eq(orgId), json.capture());
            assertThat(json.getValue()).contains("\"type\":\"inbox.refresh\"");
        }

        @Test
        void aBroadcastFailureIsSwallowed() {
            doThrow(new IllegalStateException("boom")).when(registry).broadcast(any(), any());

            assertThatCode(() -> listener().onHistoryImported(
                    new com.connecta.backend.inbox.service.HistoryImportedEvent(orgId))).doesNotThrowAnyException();
        }
    }
}
