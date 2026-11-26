package com.connecta.backend.org.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.connecta.backend.config.PresenceProperties;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;

/** The job's tick is called by hand, so no real time passes. */
class PresenceHeartbeatJobTests {

    private static final Duration INTERVAL = Duration.ofSeconds(30);

    private final TaskScheduler scheduler = mock(TaskScheduler.class);
    private final PresenceService presence = mock(PresenceService.class);
    private final AvailabilityIntervalService intervals = mock(AvailabilityIntervalService.class);
    private final PresenceHeartbeatJob job = new PresenceHeartbeatJob(
            new PresenceProperties(Duration.ofSeconds(60), INTERVAL), scheduler, presence, intervals);

    @Nested
    class Starting {

        @Test
        void schedulesTheTickAtTheConfiguredInterval() {
            job.start();

            verify(scheduler).scheduleWithFixedDelay(any(Runnable.class), eq(INTERVAL));
        }
    }

    @Nested
    class Beating {

        @Test
        void stampsTheConnectedMembersInOneCall() {
            Set<UUID> online = Set.of(UUID.randomUUID(), UUID.randomUUID());
            when(presence.connectedMembershipIds()).thenReturn(online);

            job.beat();

            verify(intervals).heartbeat(online);
        }

        @Test
        void doesNotTouchTheDatabaseWhenNobodyIsOnline() {
            when(presence.connectedMembershipIds()).thenReturn(Set.of());

            job.beat();

            verify(intervals, never()).heartbeat(any());
        }

        @Test
        void aFailureIsSwallowedAndTheNextTickStillRuns() {
            Set<UUID> online = Set.of(UUID.randomUUID());
            when(presence.connectedMembershipIds()).thenReturn(online);
            doThrow(new IllegalStateException("db down")).doNothing().when(intervals).heartbeat(online);

            job.beat();
            job.beat();

            verify(intervals, times(2)).heartbeat(online);
        }
    }
}
