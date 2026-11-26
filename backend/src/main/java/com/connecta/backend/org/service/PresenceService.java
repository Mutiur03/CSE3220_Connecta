package com.connecta.backend.org.service;

import com.connecta.backend.config.PresenceProperties;
import com.connecta.backend.org.domain.Membership;
import com.connecta.backend.org.repository.MembershipRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

/**
 * A member is online while they hold at least one realtime connection to the organization. The first
 * connection opens an availability interval; when the last one drops, the interval closes after the grace
 * period, stamped with the moment the connection dropped (the grace never inflates the hours). A reconnect
 * inside the grace cancels the pending close. Several tabs count once.
 *
 * <p>ponytail: connection counts live in this process's memory, so this is correct for one backend
 * instance only. Several instances need a shared store (Redis) for the counts, and the startup cleanup
 * would then have to close only its own instance's intervals.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PresenceService {

    private record Key(UUID organizationId, UUID userId) {
    }

    private static final class State {
        int connections;
        UUID membershipId;
        Instant droppedAt;
        ScheduledFuture<?> pendingClose;
    }

    private final PresenceProperties properties;
    private final Clock clock;
    private final TaskScheduler scheduler;
    private final MembershipRepository memberships;
    private final AvailabilityIntervalService intervals;

    private final Map<Key, State> states = new HashMap<>();

    public synchronized void connected(UUID organizationId, UUID userId) {
        State state = states.computeIfAbsent(new Key(organizationId, userId), k -> new State());
        state.connections++;
        if (state.pendingClose != null) {
            state.pendingClose.cancel(false);
            state.pendingClose = null;
        } else if (state.connections == 1) {
            findMembership(organizationId, userId).ifPresent(member -> {
                state.membershipId = member.getId();
                intervals.open(member);
            });
        }
    }

    public synchronized void disconnected(UUID organizationId, UUID userId) {
        Key key = new Key(organizationId, userId);
        State state = states.get(key);
        if (state == null || --state.connections > 0) {
            return;
        }
        state.droppedAt = clock.instant();
        state.pendingClose = scheduler.schedule(() -> closeIfStillGone(key),
                state.droppedAt.plus(properties.gracePeriod()));
    }

    /** Connected now, or inside the grace period after the last connection dropped. */
    public synchronized boolean isOnline(UUID organizationId, UUID userId) {
        return states.containsKey(new Key(organizationId, userId));
    }

    /** Members holding a connection right now; one still inside its grace period is gone and not listed. */
    public synchronized Set<UUID> connectedMembershipIds() {
        return states.values().stream().filter(state -> state.connections > 0)
                .map(state -> state.membershipId).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private synchronized void closeIfStillGone(Key key) {
        State state = states.get(key);
        if (state == null || state.connections > 0) {
            return;
        }
        states.remove(key);
        findMembership(key.organizationId(), key.userId()).ifPresent(m -> intervals.close(m, state.droppedAt));
    }

    private Optional<Membership> findMembership(UUID organizationId, UUID userId) {
        try {
            return memberships.findByOrganizationIdAndUserId(organizationId, userId);
        } catch (RuntimeException e) {
            log.warn("Presence could not load the membership of user {} in organization {}", userId,
                    organizationId, e);
            return Optional.empty();
        }
    }
}
