package com.connecta.backend.realtime.controller;

import com.connecta.backend.config.RealtimeProperties;
import com.connecta.backend.realtime.service.RealtimeGrant;
import com.connecta.backend.realtime.service.RealtimeSessionRegistry;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Receives nothing but keep-alives: a client text frame {@code ping} is answered with {@code {"type":"pong"}}
 * and counts as activity. A frame above the size cap closes the session with 1009. Everything the server pushes goes through {@link RealtimeSessionRegistry}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InboxWebSocketHandler extends TextWebSocketHandler {

    static final String PING = "ping";
    static final String PONG = "{\"type\":\"pong\"}";

    private final RealtimeSessionRegistry registry;
    private final RealtimeProperties properties;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        RealtimeGrant grant = (RealtimeGrant) session.getAttributes().get(TicketHandshakeInterceptor.ATTR_GRANT);
        // The decorator makes concurrent sends (a broadcast and a pong) safe and bounds a slow client.
        WebSocketSession safe = new ConcurrentWebSocketSessionDecorator(session,
                (int) properties.sendTimeLimit().toMillis(), properties.sendBufferSizeLimit());
        registry.register(safe, grant.organizationId(), grant.userId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        if (message.getPayloadLength() > properties.maxFrameBytes()) {
            closeQuietly(session);
            return;
        }
        registry.touch(session.getId());
        if (PING.equals(message.getPayload())) {
            registry.sendTo(session.getId(), PONG);
        }
    }

    private void closeQuietly(WebSocketSession session) {
        try {
            session.close(CloseStatus.TOO_BIG_TO_PROCESS);
        } catch (IOException e) {
            log.debug("Session {} was already closed", session.getId());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        registry.remove(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        registry.remove(session.getId());
    }
}
