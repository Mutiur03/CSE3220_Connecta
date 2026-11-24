package com.connecta.backend.realtime.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.inbox.domain.MessageDirection;
import com.connecta.backend.inbox.dto.MessageResponse;
import com.connecta.backend.inbox.service.MessageLookupService;
import com.connecta.backend.inbox.service.MessageReceivedEvent;
import com.connecta.backend.org.service.ActiveCaller;
import com.connecta.backend.org.service.TenantAccessService;
import com.connecta.backend.realtime.service.RealtimeTicketService;
import com.connecta.backend.support.MockRepositories;
import com.connecta.backend.support.TestUsers;
import com.connecta.backend.user.domain.Roles;
import com.connecta.backend.user.domain.User;
import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Real handshake over a random port (no database): origin check, single-use ticket, and the tenant
 * boundary of the push. Runs without Docker.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@MockRepositories
class InboxWebSocketIntegrationTests {

    private static final String ORIGIN = "http://localhost:3000";

    private final UUID orgA = UUID.randomUUID();
    private final UUID orgB = UUID.randomUUID();
    private final User user = TestUsers.user("agent@example.com", Roles.AGENT);

    @LocalServerPort
    int port;

    @Autowired
    RealtimeTicketService tickets;

    @Autowired
    ApplicationEventPublisher events;

    @MockitoBean
    ActiveCaller activeCaller;

    @MockitoBean
    TenantAccessService tenants;

    @MockitoBean
    MessageLookupService messages;

    private WebSocketSession connect(String ticket, String origin, BlockingQueue<String> inbox) throws Exception {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.setOrigin(origin);
        return new StandardWebSocketClient().execute(new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) {
                inbox.add(message.getPayload());
            }
        }, headers, URI.create("ws://localhost:" + port + "/ws/inbox?ticket=" + ticket)).get(5, TimeUnit.SECONDS);
    }

    private String ticketFor(UUID org) {
        when(activeCaller.load(any())).thenReturn(user);
        return tickets.issue(user.getId().toString(), org).ticket();
    }

    @Test
    void anOrganizationsMessageReachesOnlyItsOwnSessions() throws Exception {
        BlockingQueue<String> a = new LinkedBlockingQueue<>();
        BlockingQueue<String> b = new LinkedBlockingQueue<>();
        WebSocketSession sessionA = connect(ticketFor(orgA), ORIGIN, a);
        WebSocketSession sessionB = connect(ticketFor(orgB), ORIGIN, b);
        UUID messageId = UUID.randomUUID();
        when(messages.find(orgA, messageId)).thenReturn(Optional.of(new MessageResponse(messageId,
                MessageDirection.INBOUND, "hi", Instant.parse("2026-11-24T10:00:00Z"), null, null)));

        events.publishEvent(new MessageReceivedEvent(orgA, UUID.randomUUID(), messageId, Channel.INSTAGRAM,
                MessageDirection.INBOUND, false));

        assertThat(a.poll(5, TimeUnit.SECONDS)).contains("message.created").contains("\"text\":\"hi\"");
        assertThat(b.poll(500, TimeUnit.MILLISECONDS)).isNull();
        sessionA.close();
        sessionB.close();
    }

    @Test
    void aPingIsAnsweredWithAPong() throws Exception {
        BlockingQueue<String> inbox = new LinkedBlockingQueue<>();
        WebSocketSession session = connect(ticketFor(orgA), ORIGIN, inbox);

        session.sendMessage(new TextMessage("ping"));

        assertThat(inbox.poll(5, TimeUnit.SECONDS)).isEqualTo("{\"type\":\"pong\"}");
        session.close();
    }

    @Test
    void aTicketCannotBeUsedTwice() throws Exception {
        String ticket = ticketFor(orgA);
        connect(ticket, ORIGIN, new LinkedBlockingQueue<>()).close();

        assertThatThrownBy(() -> connect(ticket, ORIGIN, new LinkedBlockingQueue<>())).hasMessageContaining("401");
    }

    @Test
    void aForeignOriginIsRejectedEvenWithAValidTicket() {
        String ticket = ticketFor(orgA);

        assertThatThrownBy(() -> connect(ticket, "http://evil.example", new LinkedBlockingQueue<>()))
                .hasMessageContaining("403");
    }

    @Test
    void noTicketIsRejected() {
        assertThatThrownBy(() -> connect("", ORIGIN, new LinkedBlockingQueue<>())).hasMessageContaining("401");
    }
}
