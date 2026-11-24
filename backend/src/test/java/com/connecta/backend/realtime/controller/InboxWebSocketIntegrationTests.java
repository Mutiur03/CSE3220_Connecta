package com.connecta.backend.realtime.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.inbox.domain.MessageDirection;
import com.connecta.backend.inbox.dto.MessageResponse;
import com.connecta.backend.inbox.service.HistoryImportedEvent;
import com.connecta.backend.inbox.service.MessageLookupService;
import com.connecta.backend.inbox.service.MessageReceivedEvent;
import com.connecta.backend.inbox.service.ParticipantProfileUpdatedEvent;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.connecta.backend.org.service.ActiveCaller;
import com.connecta.backend.org.service.MemberRemovedEvent;
import com.connecta.backend.org.service.OrganizationDeletedEvent;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Real handshake over a random port (no database): origin check, single-use ticket, the tenant
 * boundary of the push, the frame cap, revocation and ticket-free logs. Runs without Docker.
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
        return connect(ticket, origin, inbox, new CompletableFuture<>());
    }

    private WebSocketSession connect(String ticket, String origin, BlockingQueue<String> inbox,
            CompletableFuture<CloseStatus> closed) throws Exception {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.setOrigin(origin);
        return new StandardWebSocketClient().execute(new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) {
                inbox.add(message.getPayload());
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
                closed.complete(status);
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
    void participantUpdatedReachesOnlyItsOwnOrganizationsSessions() throws Exception {
        BlockingQueue<String> a = new LinkedBlockingQueue<>();
        BlockingQueue<String> b = new LinkedBlockingQueue<>();
        WebSocketSession sessionA = connect(ticketFor(orgA), ORIGIN, a);
        WebSocketSession sessionB = connect(ticketFor(orgB), ORIGIN, b);
        UUID conversationId = UUID.randomUUID();

        events.publishEvent(new ParticipantProfileUpdatedEvent(orgA, conversationId));

        assertThat(a.poll(5, TimeUnit.SECONDS)).contains("participant.updated").contains(conversationId.toString());
        assertThat(b.poll(500, TimeUnit.MILLISECONDS)).isNull();
        sessionA.close();
        sessionB.close();
    }

    @Test
    void inboxRefreshReachesOnlyItsOwnOrganizationsSessions() throws Exception {
        BlockingQueue<String> a = new LinkedBlockingQueue<>();
        BlockingQueue<String> b = new LinkedBlockingQueue<>();
        WebSocketSession sessionA = connect(ticketFor(orgA), ORIGIN, a);
        WebSocketSession sessionB = connect(ticketFor(orgB), ORIGIN, b);

        events.publishEvent(new HistoryImportedEvent(orgA));

        assertThat(a.poll(5, TimeUnit.SECONDS)).contains("inbox.refresh");
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
    void aTicketSurvivesARejectedOriginAttempt() throws Exception {
        String ticket = ticketFor(orgA);

        assertThatThrownBy(() -> connect(ticket, "http://evil.example", new LinkedBlockingQueue<>()))
                .hasMessageContaining("403");

        connect(ticket, ORIGIN, new LinkedBlockingQueue<>()).close();
    }

    @Test
    void anOversizedFrameClosesTheSessionWith1009() throws Exception {
        CompletableFuture<CloseStatus> closed = new CompletableFuture<>();
        WebSocketSession session = connect(ticketFor(orgA), ORIGIN, new LinkedBlockingQueue<>(), closed);

        session.sendMessage(new TextMessage("x".repeat(2048)));

        assertThat(closed.get(5, TimeUnit.SECONDS).getCode()).isEqualTo(1009);
    }

    @Test
    void aRemovedMemberIsDisconnectedWithCode4003() throws Exception {
        CompletableFuture<CloseStatus> closed = new CompletableFuture<>();
        connect(ticketFor(orgA), ORIGIN, new LinkedBlockingQueue<>(), closed);

        events.publishEvent(new MemberRemovedEvent(orgA, user.getId()));

        assertThat(closed.get(5, TimeUnit.SECONDS).getCode()).isEqualTo(4003);
    }

    @Test
    void aDeletedOrganizationDisconnectsEverySessionOfItWithCode4003() throws Exception {
        CompletableFuture<CloseStatus> closed = new CompletableFuture<>();
        connect(ticketFor(orgA), ORIGIN, new LinkedBlockingQueue<>(), closed);

        events.publishEvent(new OrganizationDeletedEvent(orgA));

        assertThat(closed.get(5, TimeUnit.SECONDS).getCode()).isEqualTo(4003);
    }

    /** At the default log levels; Spring Security's DEBUG line "Securing GET ..." would print the query. */
    @Test
    void theTicketNeverAppearsInLogs() throws Exception {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
        try {
            String good = ticketFor(orgA);
            String bad = ticketFor(orgA);
            connect(good, ORIGIN, new LinkedBlockingQueue<>()).close();
            assertThatThrownBy(() -> connect(bad, "http://evil.example", new LinkedBlockingQueue<>()));
            assertThatThrownBy(() -> connect(good, ORIGIN, new LinkedBlockingQueue<>()));

            assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                    .noneMatch(m -> m.contains(good) || m.contains(bad));
        } finally {
            root.detachAppender(appender);
        }
    }

    @Test
    void noTicketIsRejected() {
        assertThatThrownBy(() -> connect("", ORIGIN, new LinkedBlockingQueue<>())).hasMessageContaining("401");
    }
}
