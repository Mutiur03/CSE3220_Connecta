package com.connecta.backend.config;

import com.connecta.backend.realtime.controller.InboxWebSocketHandler;
import com.connecta.backend.realtime.controller.TicketHandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Registers /ws/inbox. Only the frontend origin may connect (blocks cross-site WebSocket hijacking); a valid
 * ticket is then required by {@link TicketHandshakeInterceptor}.
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
class WebSocketConfig implements WebSocketConfigurer {

    public static final String INBOX_PATH = "/ws/inbox";

    private final InboxWebSocketHandler handler;
    private final TicketHandshakeInterceptor ticketInterceptor;
    private final FrontendProperties frontend;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, INBOX_PATH)
                .addInterceptors(ticketInterceptor)
                .setAllowedOrigins(frontend.baseUrl());
    }
}
