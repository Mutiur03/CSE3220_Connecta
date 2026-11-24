package com.connecta.backend.config;

import com.connecta.backend.realtime.controller.InboxWebSocketHandler;
import com.connecta.backend.realtime.controller.TicketHandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import jakarta.websocket.server.ServerContainer;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.ServletContextAware;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.support.OriginHandshakeInterceptor;

/**
 * Registers /ws/inbox. Only the frontend origin may connect (blocks cross-site WebSocket hijacking); a valid
 * ticket is then required by {@link TicketHandshakeInterceptor}. Spring appends its own origin check after
 * the interceptors given here, so the origin interceptor is listed first on purpose: a foreign Origin must
 * be refused before a ticket is redeemed, or it would burn the ticket.
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
class WebSocketConfig implements WebSocketConfigurer {

    public static final String INBOX_PATH = "/ws/inbox";

    private final InboxWebSocketHandler handler;
    private final TicketHandshakeInterceptor ticketInterceptor;
    private final FrontendProperties frontend;
    private final RealtimeProperties properties;

    /**
     * Container-level cap on a text frame, so an oversized frame is refused (close 1009) before it is
     * buffered. Skipped when there is no WebSocket container (MockMvc tests have none).
     */
    @Bean
    ServletContextAware textFrameCap() {
        return servletContext -> {
            Object container = servletContext.getAttribute(ServerContainer.class.getName());
            if (container instanceof ServerContainer serverContainer) {
                serverContainer.setDefaultMaxTextMessageBufferSize(properties.maxFrameBytes());
            }
        };
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, INBOX_PATH)
                .addInterceptors(new OriginHandshakeInterceptor(List.of(frontend.baseUrl())), ticketInterceptor)
                .setAllowedOrigins(frontend.baseUrl());
    }
}
