package com.connecta.backend.realtime.service;

import com.connecta.backend.config.RealtimeProperties;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * Every open /ws/inbox session, each bound to one organization and one user when it connected. A broadcast
 * goes only to the sessions of the given organization, which is the tenant boundary of live updates. A
 * session that fails to send is dropped alone.
 *
 * <p>ponytail: in-memory and per instance, so a second backend instance would not see this instance's
 * sessions. A broker (Redis pub/sub, a queue) is the upgrade; no interface until there is a second one.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RealtimeSessionRegistry {

    public static final CloseStatus IDLE_TIMEOUT = new CloseStatus(4001, "Idle timeout");
    public static final CloseStatus REPLACED = new CloseStatus(4002, "Too many sessions");

    private record Entry(WebSocketSession session, UUID organizationId, UUID userId, Instant connectedAt,
            AtomicReference<Instant> lastSeen) {
    }

    private final RealtimeProperties properties;
    private final Clock clock;

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    /** Adds the session. Past the per-user limit the user's oldest sessions are closed. */
    public synchronized void register(WebSocketSession session, UUID organizationId, UUID userId) {
        Instant now = clock.instant();
        List<Entry> own = entries.values().stream().filter(e -> e.userId().equals(userId))
                .sorted(Comparator.comparing(Entry::connectedAt)).toList();
        int excess = own.size() - properties.maxSessionsPerUser() + 1;
        for (int i = 0; i < excess; i++) {
            drop(own.get(i), REPLACED);
        }
        entries.put(session.getId(), new Entry(session, organizationId, userId, now, new AtomicReference<>(now)));
    }

    public void remove(String sessionId) {
        entries.remove(sessionId);
    }

    public void touch(String sessionId) {
        Entry entry = entries.get(sessionId);
        if (entry != null) {
            entry.lastSeen().set(clock.instant());
        }
    }

    /** Sends to one session (the pong). A failure drops that session. */
    public void sendTo(String sessionId, String json) {
        Entry entry = entries.get(sessionId);
        if (entry != null) {
            send(entry, json);
        }
    }

    /** Sends to every session of the organization and to no other. */
    public void broadcast(UUID organizationId, String json) {
        entries.values().stream().filter(e -> e.organizationId().equals(organizationId))
                .forEach(e -> send(e, json));
    }

    public int count(UUID organizationId) {
        return (int) entries.values().stream().filter(e -> e.organizationId().equals(organizationId)).count();
    }

    /** Closes sessions that sent nothing for the idle timeout and forgets sessions that already closed. */
    @Scheduled(fixedDelayString = "${app.realtime.sweep-interval:30s}")
    public void sweep() {
        Instant cutoff = clock.instant().minus(properties.idleTimeout());
        for (Entry entry : List.copyOf(entries.values())) {
            if (!entry.session().isOpen()) {
                entries.remove(entry.session().getId());
            } else if (entry.lastSeen().get().isBefore(cutoff)) {
                drop(entry, IDLE_TIMEOUT);
            }
        }
    }

    private void send(Entry entry, String json) {
        try {
            if (!entry.session().isOpen()) {
                entries.remove(entry.session().getId());
                return;
            }
            entry.session().sendMessage(new TextMessage(json));
        } catch (IOException | RuntimeException e) {
            log.warn("Dropping realtime session {}: send failed ({})", entry.session().getId(),
                    e.getClass().getSimpleName());
            drop(entry, CloseStatus.SERVER_ERROR);
        }
    }

    private void drop(Entry entry, CloseStatus status) {
        entries.remove(entry.session().getId());
        try {
            entry.session().close(status);
        } catch (IOException | RuntimeException e) {
            log.debug("Realtime session {} was already closed", entry.session().getId());
        }
    }
}
