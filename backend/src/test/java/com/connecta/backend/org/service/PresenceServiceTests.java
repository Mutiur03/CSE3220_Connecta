package com.connecta.backend.org.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.connecta.backend.config.PresenceProperties;
import com.connecta.backend.org.domain.Membership;
import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.org.repository.MembershipRepository;
import com.connecta.backend.support.MutableClock;
import com.connecta.backend.support.TestUsers;
import com.connecta.backend.user.domain.Role;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;

/** The scheduler is a mock: the test runs the captured close itself, so no real time passes. */
@ExtendWith(MockitoExtension.class)
class PresenceServiceTests {

    private static final Duration GRACE = Duration.ofSeconds(60);

    @Mock
    MembershipRepository memberships;

    @Mock
    AvailabilityIntervalService intervals;

    @Mock
    TaskScheduler scheduler;

    @SuppressWarnings("rawtypes")
    final ScheduledFuture pending = mock(ScheduledFuture.class);

    final MutableClock clock = new MutableClock(Instant.parse("2026-11-27T09:00:00Z"));
    final Organization acme = new Organization("Acme", "acme");
    final Organization beta = new Organization("Beta", "beta");
    final UUID userA = UUID.randomUUID();
    final UUID userB = UUID.randomUUID();
    Membership acmeA;
    Membership acmeB;
    PresenceService presence;

    @BeforeEach
    void setUp() {
        TestUsers.set(acme, "id", UUID.randomUUID());
        TestUsers.set(beta, "id", UUID.randomUUID());
        acmeA = member(acme);
        acmeB = member(acme);
        presence = new PresenceService(new PresenceProperties(GRACE, Duration.ofSeconds(30)), clock, scheduler, memberships, intervals);
    }

    private Membership member(Organization organization) {
        Membership m = new Membership(organization,
                TestUsers.user("x" + UUID.randomUUID() + "@example.com", "AGENT"), new Role("AGENT"));
        TestUsers.set(m, "id", UUID.randomUUID());
        return m;
    }

    private void knows(Organization organization, UUID user, Membership membership) {
        when(memberships.findByOrganizationIdAndUserId(organization.getId(), user))
                .thenReturn(Optional.of(membership));
    }

    private Runnable scheduledClose(Instant expectedAt) {
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).schedule(task.capture(), eq(expectedAt));
        return task.getValue();
    }

    @SuppressWarnings("unchecked")
    private void schedulerReturnsPending() {
        when(scheduler.schedule(any(Runnable.class), any(Instant.class))).thenReturn(pending);
    }

    @Nested
    class Connecting {

        @Test
        void theFirstConnectionOpensAnIntervalAndMarksOnline() {
            knows(acme, userA, acmeA);

            presence.connected(acme.getId(), userA);

            verify(intervals).open(acmeA);
            assertTrue(presence.isOnline(acme.getId(), userA));
        }

        @Test
        void aSecondTabOpensNoSecondInterval() {
            knows(acme, userA, acmeA);

            presence.connected(acme.getId(), userA);
            presence.connected(acme.getId(), userA);

            verify(intervals, times(1)).open(any());
        }

        @Test
        void twoMembersAreIndependent() {
            knows(acme, userA, acmeA);

            presence.connected(acme.getId(), userA);

            assertTrue(presence.isOnline(acme.getId(), userA));
            assertFalse(presence.isOnline(acme.getId(), userB));
        }

        @Test
        void theSameUserInAnotherOrganizationIsAnotherPresence() {
            knows(acme, userA, acmeA);

            presence.connected(acme.getId(), userA);

            assertFalse(presence.isOnline(beta.getId(), userA));
        }

        @Test
        void aMissingMembershipOpensNothingButDoesNotFail() {
            when(memberships.findByOrganizationIdAndUserId(acme.getId(), userA)).thenReturn(Optional.empty());

            presence.connected(acme.getId(), userA);

            verify(intervals, never()).open(any());
        }

        @Test
        void aLookupFailureIsSwallowedAndLogged() {
            when(memberships.findByOrganizationIdAndUserId(acme.getId(), userA))
                    .thenThrow(new IllegalStateException("db down"));

            presence.connected(acme.getId(), userA);

            verify(intervals, never()).open(any());
        }
    }

    @Nested
    class ConnectedMembershipIds {

        @Test
        void listsEachConnectedMemberOnceHoweverManyTabs() {
            knows(acme, userA, acmeA);
            knows(acme, userB, acmeB);

            presence.connected(acme.getId(), userA);
            presence.connected(acme.getId(), userA);
            presence.connected(acme.getId(), userB);

            assertEquals(Set.of(acmeA.getId(), acmeB.getId()), presence.connectedMembershipIds());
        }

        @Test
        void leavesOutAMemberInsideTheirGracePeriod() {
            knows(acme, userA, acmeA);
            knows(acme, userB, acmeB);
            schedulerReturnsPending();
            presence.connected(acme.getId(), userA);
            presence.connected(acme.getId(), userB);

            presence.disconnected(acme.getId(), userA);

            assertEquals(Set.of(acmeB.getId()), presence.connectedMembershipIds());
        }

        @Test
        void listsAMemberAgainAfterAReconnectInsideTheGrace() {
            knows(acme, userA, acmeA);
            schedulerReturnsPending();
            presence.connected(acme.getId(), userA);
            presence.disconnected(acme.getId(), userA);

            presence.connected(acme.getId(), userA);

            assertEquals(Set.of(acmeA.getId()), presence.connectedMembershipIds());
        }
    }

    @Nested
    class Disconnecting {

        @Test
        void aRemainingTabKeepsTheMemberOnlineAndSchedulesNothing() {
            knows(acme, userA, acmeA);
            presence.connected(acme.getId(), userA);
            presence.connected(acme.getId(), userA);

            presence.disconnected(acme.getId(), userA);

            verify(scheduler, never()).schedule(any(Runnable.class), any(Instant.class));
            assertTrue(presence.isOnline(acme.getId(), userA));
        }

        @Test
        void theLastDisconnectSchedulesTheCloseAfterTheGraceAndStaysOnlineMeanwhile() {
            knows(acme, userA, acmeA);
            schedulerReturnsPending();
            presence.connected(acme.getId(), userA);

            presence.disconnected(acme.getId(), userA);

            scheduledClose(clock.instant().plus(GRACE));
            verify(intervals, never()).close(any(), any());
            assertTrue(presence.isOnline(acme.getId(), userA));
        }

        @Test
        void aReconnectInsideTheGraceCancelsTheCloseAndOpensNoNewInterval() {
            knows(acme, userA, acmeA);
            schedulerReturnsPending();
            presence.connected(acme.getId(), userA);
            presence.disconnected(acme.getId(), userA);
            Runnable close = scheduledClose(clock.instant().plus(GRACE));

            clock.advance(Duration.ofSeconds(10));
            presence.connected(acme.getId(), userA);
            close.run();

            verify(pending).cancel(false);
            verify(intervals, times(1)).open(any());
            verify(intervals, never()).close(any(), any());
            assertTrue(presence.isOnline(acme.getId(), userA));
        }

        @Test
        void withoutReconnectTheIntervalEndsWhenTheConnectionDroppedNotAfterTheGrace() {
            knows(acme, userA, acmeA);
            schedulerReturnsPending();
            presence.connected(acme.getId(), userA);
            clock.advance(Duration.ofMinutes(5));
            Instant droppedAt = clock.instant();
            presence.disconnected(acme.getId(), userA);
            Runnable close = scheduledClose(droppedAt.plus(GRACE));

            clock.advance(GRACE);
            close.run();

            verify(intervals).close(acmeA, droppedAt);
            assertFalse(presence.isOnline(acme.getId(), userA));
        }

        @Test
        void aDisconnectOfAnUnknownMemberDoesNothing() {
            presence.disconnected(acme.getId(), userA);

            verify(scheduler, never()).schedule(any(Runnable.class), any(Instant.class));
        }

        @Test
        void closingOneMemberLeavesTheOtherOnline() {
            knows(acme, userA, acmeA);
            knows(acme, userB, acmeB);
            schedulerReturnsPending();
            presence.connected(acme.getId(), userA);
            presence.connected(acme.getId(), userB);
            Instant droppedAt = clock.instant();
            presence.disconnected(acme.getId(), userA);

            scheduledClose(droppedAt.plus(GRACE)).run();

            verify(intervals).close(acmeA, droppedAt);
            verify(intervals, never()).close(eq(acmeB), any());
            assertTrue(presence.isOnline(acme.getId(), userB));
            assertFalse(presence.isOnline(acme.getId(), userA));
        }
    }
}
