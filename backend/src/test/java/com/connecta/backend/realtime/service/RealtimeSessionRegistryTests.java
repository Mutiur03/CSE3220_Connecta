package com.connecta.backend.realtime.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.connecta.backend.config.RealtimeProperties;
import com.connecta.backend.support.MutableClock;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

class RealtimeSessionRegistryTests {

    private final UUID orgA = UUID.randomUUID();
    private final UUID orgB = UUID.randomUUID();
    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-24T10:00:00Z"));
    private RealtimeSessionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new RealtimeSessionRegistry(new RealtimeProperties(Duration.ofSeconds(30),
                Duration.ofSeconds(90), Duration.ofSeconds(30), 2, Duration.ofSeconds(5), 65536, 5, 20,
                Duration.ofMinutes(1), 1024), clock);
    }

    private static WebSocketSession session(String id) {
        WebSocketSession s = mock(WebSocketSession.class);
        when(s.getId()).thenReturn(id);
        when(s.isOpen()).thenReturn(true);
        return s;
    }

    @Nested
    class Broadcast {

        @Test
        void reachesOnlyTheSessionsOfThatOrganization() throws IOException {
            WebSocketSession a = session("a");
            WebSocketSession b = session("b");
            registry.register(a, orgA, UUID.randomUUID());
            registry.register(b, orgB, UUID.randomUUID());

            registry.broadcast(orgA, "{\"x\":1}");

            verify(a).sendMessage(new TextMessage("{\"x\":1}"));
            verify(b, never()).sendMessage(any());
        }

        @Test
        void anOrganizationWithNoSessionsSendsNothing() throws IOException {
            WebSocketSession b = session("b");
            registry.register(b, orgB, UUID.randomUUID());

            registry.broadcast(orgA, "x");

            verify(b, never()).sendMessage(any());
        }

        @Test
        void aFailingSessionIsDroppedAndTheOthersStillReceive() throws IOException {
            WebSocketSession bad = session("bad");
            WebSocketSession good = session("good");
            doThrow(new IOException("broken")).when(bad).sendMessage(any());
            registry.register(bad, orgA, UUID.randomUUID());
            registry.register(good, orgA, UUID.randomUUID());

            registry.broadcast(orgA, "x");

            verify(good).sendMessage(any());
            verify(bad).close(CloseStatus.SERVER_ERROR);
            assertThat(registry.count(orgA)).isEqualTo(1);
        }

        @Test
        void aClosedSessionIsSkippedAndForgotten() throws IOException {
            WebSocketSession closed = session("closed");
            registry.register(closed, orgA, UUID.randomUUID());
            when(closed.isOpen()).thenReturn(false);

            registry.broadcast(orgA, "x");

            verify(closed, never()).sendMessage(any());
            assertThat(registry.count(orgA)).isZero();
        }
    }

    @Nested
    class CloseSessions {

        @Test
        void closesOnlyThatUsersSessionsInThatOrganization() throws IOException {
            UUID user = UUID.randomUUID();
            WebSocketSession target = session("target");
            WebSocketSession sameUserOtherOrg = session("otherOrg");
            WebSocketSession otherUserSameOrg = session("otherUser");
            registry.register(target, orgA, user);
            registry.register(sameUserOtherOrg, orgB, user);
            registry.register(otherUserSameOrg, orgA, UUID.randomUUID());

            registry.closeSessions(orgA, user, RealtimeSessionRegistry.ACCESS_REVOKED);

            verify(target).close(RealtimeSessionRegistry.ACCESS_REVOKED);
            verify(sameUserOtherOrg, never()).close(any(CloseStatus.class));
            verify(otherUserSameOrg, never()).close(any(CloseStatus.class));
            assertThat(registry.count(orgA)).isEqualTo(1);
            assertThat(registry.count(orgB)).isEqualTo(1);
        }

        @Test
        void closeOrganizationClosesEveryUserOfThatOrganizationOnly() throws IOException {
            WebSocketSession first = session("first");
            WebSocketSession second = session("second");
            WebSocketSession otherOrg = session("other");
            registry.register(first, orgA, UUID.randomUUID());
            registry.register(second, orgA, UUID.randomUUID());
            registry.register(otherOrg, orgB, UUID.randomUUID());

            registry.closeOrganization(orgA, RealtimeSessionRegistry.ACCESS_REVOKED);

            verify(first).close(RealtimeSessionRegistry.ACCESS_REVOKED);
            verify(second).close(RealtimeSessionRegistry.ACCESS_REVOKED);
            verify(otherOrg, never()).close(any(CloseStatus.class));
            assertThat(registry.count(orgA)).isZero();
            assertThat(registry.count(orgB)).isEqualTo(1);
        }

        @Test
        void usesCloseCode4003() {
            assertThat(RealtimeSessionRegistry.ACCESS_REVOKED.getCode()).isEqualTo(4003);
        }
    }

    @Nested
    class CloseUser {

        @Test
        void closesTheUsersSessionsInEveryOrganizationAndNoOneElses() throws IOException {
            UUID user = UUID.randomUUID();
            WebSocketSession inA = session("inA");
            WebSocketSession inB = session("inB");
            WebSocketSession other = session("other");
            registry.register(inA, orgA, user);
            registry.register(inB, orgB, user);
            registry.register(other, orgA, UUID.randomUUID());

            registry.closeUser(user, RealtimeSessionRegistry.ACCESS_REVOKED);

            verify(inA).close(RealtimeSessionRegistry.ACCESS_REVOKED);
            verify(inB).close(RealtimeSessionRegistry.ACCESS_REVOKED);
            verify(other, never()).close(any(CloseStatus.class));
            assertThat(registry.count(orgA)).isEqualTo(1);
            assertThat(registry.count(orgB)).isZero();
        }
    }

    @Nested
    class Lifecycle {

        @Test
        void removeForgetsTheSession() {
            registry.register(session("a"), orgA, UUID.randomUUID());

            registry.remove("a");

            assertThat(registry.count(orgA)).isZero();
        }

        @Test
        void theOldestSessionIsClosedPastThePerUserLimit() throws IOException {
            UUID user = UUID.randomUUID();
            WebSocketSession first = session("1");
            registry.register(first, orgA, user);
            clock.advance(Duration.ofSeconds(1));
            registry.register(session("2"), orgA, user);
            clock.advance(Duration.ofSeconds(1));

            registry.register(session("3"), orgA, user);

            verify(first).close(RealtimeSessionRegistry.REPLACED);
            assertThat(registry.count(orgA)).isEqualTo(2);
        }

        @Test
        void theLimitIsPerUser() throws IOException {
            WebSocketSession other = session("other");
            registry.register(other, orgA, UUID.randomUUID());
            UUID user = UUID.randomUUID();
            registry.register(session("1"), orgA, user);
            registry.register(session("2"), orgA, user);

            verify(other, never()).close(any(CloseStatus.class));
            assertThat(registry.count(orgA)).isEqualTo(3);
        }

        @Test
        void sweepClosesIdleSessionsButNotActiveOnes() throws IOException {
            WebSocketSession idle = session("idle");
            WebSocketSession active = session("active");
            registry.register(idle, orgA, UUID.randomUUID());
            registry.register(active, orgA, UUID.randomUUID());
            clock.advance(Duration.ofSeconds(60));
            registry.touch("active");
            clock.advance(Duration.ofSeconds(31));

            registry.sweep();

            verify(idle).close(RealtimeSessionRegistry.IDLE_TIMEOUT);
            verify(active, never()).close(any(CloseStatus.class));
            assertThat(registry.count(orgA)).isEqualTo(1);
        }

        @Test
        void sweepForgetsSessionsThatAlreadyClosed() {
            WebSocketSession gone = session("gone");
            registry.register(gone, orgA, UUID.randomUUID());
            when(gone.isOpen()).thenReturn(false);

            registry.sweep();

            assertThat(registry.count(orgA)).isZero();
        }

        @Test
        void sendToDeliversToOneSession() throws IOException {
            WebSocketSession a = session("a");
            registry.register(a, orgA, UUID.randomUUID());

            registry.sendTo("a", "pong");

            verify(a).sendMessage(new TextMessage("pong"));
        }
    }
}
