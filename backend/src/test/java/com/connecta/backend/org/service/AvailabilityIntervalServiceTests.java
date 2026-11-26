package com.connecta.backend.org.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.connecta.backend.org.domain.AvailabilityInterval;
import com.connecta.backend.org.domain.Membership;
import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.org.repository.AvailabilityIntervalRepository;
import com.connecta.backend.support.TestClock;
import com.connecta.backend.support.TestUsers;
import com.connecta.backend.user.domain.Role;
import com.connecta.backend.user.domain.User;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AvailabilityIntervalServiceTests {

    private static final Instant EARLIER = TestClock.NOW.minusSeconds(3600);

    @Mock
    AvailabilityIntervalRepository intervals;

    AvailabilityIntervalService service;

    private final Organization organization = new Organization("Acme", "acme");
    private final Membership member = membership();

    @BeforeEach
    void setUp() {
        TestUsers.set(organization, "id", UUID.randomUUID());
        TestUsers.set(member, "id", UUID.randomUUID());
        service = new AvailabilityIntervalService(intervals, TestClock.FIXED);
    }

    private Membership membership() {
        return new Membership(organization, TestUsers.user("a@example.com", "AGENT"), new Role("AGENT"));
    }

    private void openIntervalExists(AvailabilityInterval interval) {
        when(intervals.findByMembershipIdAndOrganizationIdAndEndedAtIsNull(member.getId(), organization.getId()))
                .thenReturn(Optional.ofNullable(interval));
    }

    @Nested
    class Open {

        @Test
        void startsAnIntervalAtTheClockInstant() {
            openIntervalExists(null);

            service.open(member);

            ArgumentCaptor<AvailabilityInterval> saved = ArgumentCaptor.forClass(AvailabilityInterval.class);
            verify(intervals).save(saved.capture());
            assertEquals(TestClock.NOW, saved.getValue().getStartedAt());
            assertNull(saved.getValue().getEndedAt());
            assertEquals(member, saved.getValue().getMembership());
        }

        @Test
        void doesNothingWhenAnIntervalIsAlreadyOpen() {
            openIntervalExists(new AvailabilityInterval(member, EARLIER));

            service.open(member);

            verify(intervals, never()).save(any());
        }
    }

    @Nested
    class Close {

        @Test
        void endsTheOpenIntervalAtTheClockInstant() {
            AvailabilityInterval open = new AvailabilityInterval(member, EARLIER);
            openIntervalExists(open);

            service.close(member);

            assertEquals(TestClock.NOW, open.getEndedAt());
        }

        @Test
        void doesNothingWhenNoIntervalIsOpen() {
            openIntervalExists(null);

            service.close(member);

            verify(intervals, never()).save(any());
        }
    }

    @Nested
    class CloseAllForUser {

        @Test
        void endsEveryOpenIntervalOfTheUser() {
            UUID userId = UUID.randomUUID();
            AvailabilityInterval first = new AvailabilityInterval(member, EARLIER);
            AvailabilityInterval second = new AvailabilityInterval(membership(), EARLIER);
            when(intervals.findByMembershipUserIdAndEndedAtIsNull(userId)).thenReturn(List.of(first, second));

            service.closeAllForUser(userId);

            assertEquals(TestClock.NOW, first.getEndedAt());
            assertEquals(TestClock.NOW, second.getEndedAt());
        }

        @Test
        void doesNothingWithoutOpenIntervals() {
            UUID userId = UUID.randomUUID();
            when(intervals.findByMembershipUserIdAndEndedAtIsNull(userId)).thenReturn(List.of());

            service.closeAllForUser(userId);

            verify(intervals, never()).save(any());
        }
    }
}
