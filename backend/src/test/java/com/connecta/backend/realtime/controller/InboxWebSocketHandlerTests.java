package com.connecta.backend.realtime.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.connecta.backend.config.RealtimeProperties;
import com.connecta.backend.realtime.service.RealtimeGrant;
import com.connecta.backend.realtime.service.RealtimeSessionRegistry;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

/** The handler alone, with the registry mocked: frame limits and the ping/pong exchange. */
class InboxWebSocketHandlerTests {

    private static final int MAX_FRAME_BYTES = 16;

    private final RealtimeSessionRegistry registry = mock(RealtimeSessionRegistry.class);
    private final InboxWebSocketHandler handler = new InboxWebSocketHandler(registry,
            new RealtimeProperties(Duration.ofSeconds(30), Duration.ofSeconds(90), Duration.ofSeconds(30), 5,
                    Duration.ofSeconds(5), 65536, 5, 20, Duration.ofMinutes(1), MAX_FRAME_BYTES));
    private final WebSocketSession session = mock(WebSocketSession.class);

    @Nested
    class Connect {

        @Test
        void registersTheSessionUnderTheGrantedOrganizationAndUser() {
            RealtimeGrant grant = new RealtimeGrant(UUID.randomUUID(), UUID.randomUUID(), Instant.now());
            Map<String, Object> attributes = new HashMap<>();
            attributes.put(TicketHandshakeInterceptor.ATTR_GRANT, grant);
            when(session.getAttributes()).thenReturn(attributes);

            handler.afterConnectionEstablished(session);

            ArgumentCaptor<WebSocketSession> registered = ArgumentCaptor.forClass(WebSocketSession.class);
            verify(registry).register(registered.capture(), org.mockito.ArgumentMatchers.eq(grant.organizationId()),
                    org.mockito.ArgumentMatchers.eq(grant.userId()));
            assertThat(registered.getValue()).isInstanceOf(ConcurrentWebSocketSessionDecorator.class);
        }
    }

    @Nested
    class Messages {

        @Test
        void aPingRefreshesTheSessionAndIsAnsweredWithAPong() {
            when(session.getId()).thenReturn("s1");

            handler.handleTextMessage(session, new TextMessage("ping"));

            verify(registry).touch("s1");
            verify(registry).sendTo("s1", InboxWebSocketHandler.PONG);
        }

        @Test
        void anyOtherShortTextCountsAsActivityButGetsNoReply() {
            when(session.getId()).thenReturn("s1");

            handler.handleTextMessage(session, new TextMessage("hello"));

            verify(registry).touch("s1");
            verify(registry, never()).sendTo(any(), any());
        }

        @Test
        void anOversizedFrameClosesTheSessionAndIsNotTreatedAsActivity() throws IOException {
            handler.handleTextMessage(session, new TextMessage("x".repeat(MAX_FRAME_BYTES + 1)));

            verify(session).close(CloseStatus.TOO_BIG_TO_PROCESS);
            verify(registry, never()).touch(any());
        }

        @Test
        void anAlreadyClosedSessionDoesNotFailTheOversizedFramePath() throws IOException {
            doThrow(new IOException("closed")).when(session).close(CloseStatus.TOO_BIG_TO_PROCESS);
            when(session.getId()).thenReturn("s1");

            handler.handleTextMessage(session, new TextMessage("x".repeat(MAX_FRAME_BYTES + 1)));

            verify(registry, never()).sendTo(any(), any());
        }
    }

    @Nested
    class Disconnect {

        @Test
        void aClosedConnectionIsForgotten() {
            when(session.getId()).thenReturn("s1");

            handler.afterConnectionClosed(session, CloseStatus.NORMAL);

            verify(registry).remove("s1");
        }

        @Test
        void aTransportErrorForgetsTheSession() {
            when(session.getId()).thenReturn("s1");

            handler.handleTransportError(session, new IOException("reset"));

            verify(registry).remove("s1");
        }
    }
}
