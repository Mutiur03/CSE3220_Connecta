package com.connecta.backend.realtime.controller;

import com.connecta.backend.realtime.service.RealtimeGrant;
import com.connecta.backend.realtime.service.RealtimeTicketService;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * The authorization of /ws/inbox: the {@code ticket} query parameter is redeemed (single use, expiring) and
 * the organization and user it was issued for are bound to the session. No valid ticket means 401 and no
 * upgrade. The Origin is checked before this runs (see WebSocketConfig).
 */
@Component
@RequiredArgsConstructor
public class TicketHandshakeInterceptor implements HandshakeInterceptor {

    public static final String TICKET_PARAM = "ticket";
    public static final String ATTR_GRANT = "realtimeGrant";

    private final RealtimeTicketService tickets;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler,
            Map<String, Object> attributes) {
        Optional<RealtimeGrant> grant = Optional.of(request).filter(ServletServerHttpRequest.class::isInstance)
                .map(r -> ((ServletServerHttpRequest) r).getServletRequest().getParameter(TICKET_PARAM))
                .flatMap(tickets::redeem);
        if (grant.isEmpty()) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        attributes.put(ATTR_GRANT, grant.get());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler,
            Exception exception) {
        // nothing to clean up
    }
}
