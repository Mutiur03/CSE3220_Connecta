package com.connecta.backend.realtime.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.connecta.backend.realtime.service.RealtimeGrant;
import com.connecta.backend.realtime.service.RealtimeTicketService;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.socket.WebSocketHandler;

class TicketHandshakeInterceptorTests {

    private final RealtimeTicketService tickets = mock(RealtimeTicketService.class);
    private final TicketHandshakeInterceptor interceptor = new TicketHandshakeInterceptor(tickets);
    private final WebSocketHandler handler = mock(WebSocketHandler.class);
    private final Map<String, Object> attributes = new HashMap<>();
    private final MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/ws/inbox");
    private final MockHttpServletResponse servletResponse = new MockHttpServletResponse();

    private boolean handshake() {
        return interceptor.beforeHandshake(new ServletServerHttpRequest(servletRequest),
                new ServletServerHttpResponse(servletResponse), handler, attributes);
    }

    @Nested
    class BeforeHandshake {

        @Test
        void aValidTicketBindsTheGrantToTheSession() {
            RealtimeGrant grant = new RealtimeGrant(UUID.randomUUID(), UUID.randomUUID(), Instant.now());
            servletRequest.setParameter("ticket", "good");
            when(tickets.redeem("good")).thenReturn(Optional.of(grant));

            assertThat(handshake()).isTrue();
            assertThat(attributes).containsEntry(TicketHandshakeInterceptor.ATTR_GRANT, grant);
        }

        @Test
        void anExpiredOrReusedTicketIsUnauthorized() {
            servletRequest.setParameter("ticket", "used");
            when(tickets.redeem("used")).thenReturn(Optional.empty());

            assertThat(handshake()).isFalse();
            assertThat(servletResponse.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
            assertThat(attributes).isEmpty();
        }

        @Test
        void aMissingTicketIsUnauthorized() {
            when(tickets.redeem(null)).thenReturn(Optional.empty());

            assertThat(handshake()).isFalse();
            assertThat(servletResponse.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        }
    }
}
