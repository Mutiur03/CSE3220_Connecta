package com.connecta.backend.org.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DailyAvailabilityTests {

    private static final LocalDate FROM = LocalDate.parse("2026-11-10");
    private static final Instant NOW = Instant.parse("2026-11-15T12:00:00Z");
    private static final long HOUR = 3600;

    private static long[] clip(int days, String start, String end) {
        long[] perDay = new long[days];
        DailyAvailability.add(perDay, FROM, Instant.parse(start), end == null ? null : Instant.parse(end), NOW);
        return perDay;
    }

    @Nested
    class Closed {

        @Test
        void insideOneDayCountsItsDuration() {
            assertArrayEquals(new long[] {0, 2 * HOUR, 0},
                    clip(3, "2026-11-11T09:00:00Z", "2026-11-11T11:00:00Z"));
        }

        @Test
        void crossingMidnightSplitsAcrossTwoDays() {
            assertArrayEquals(new long[] {HOUR, 2 * HOUR, 0},
                    clip(3, "2026-11-10T23:00:00Z", "2026-11-11T02:00:00Z"));
        }

        @Test
        void spanningThreeDaysFillsTheMiddleDay() {
            assertArrayEquals(new long[] {2 * HOUR, 24 * HOUR, 3 * HOUR},
                    clip(3, "2026-11-10T22:00:00Z", "2026-11-12T03:00:00Z"));
        }

        @Test
        void entirelyBeforeOrAfterTheRangeIsIgnored() {
            assertArrayEquals(new long[] {0, 0}, clip(2, "2026-11-01T00:00:00Z", "2026-11-02T00:00:00Z"));
            assertArrayEquals(new long[] {0, 0}, clip(2, "2026-11-13T00:00:00Z", "2026-11-13T05:00:00Z"));
        }

        @Test
        void partOutsideTheRangeIsClamped() {
            assertArrayEquals(new long[] {2 * HOUR, 0},
                    clip(2, "2026-11-09T22:00:00Z", "2026-11-10T02:00:00Z"));
        }

        @Test
        void partialSecondsAreFloored() {
            assertArrayEquals(new long[] {1},
                    clip(1, "2026-11-10T00:00:00.100Z", "2026-11-10T00:00:01.900Z"));
        }
    }

    @Nested
    class Open {

        @Test
        void countsUpToNowAndNeverTheFuture() {
            assertArrayEquals(new long[] {0, 0, 0, 0, 2 * HOUR, 12 * HOUR, 0},
                    clip(7, "2026-11-14T22:00:00Z", null));
        }

        @Test
        void anEndAfterNowIsCappedAtNow() {
            assertArrayEquals(new long[] {0, 0, 0, 0, 0, 12 * HOUR, 0},
                    clip(7, "2026-11-15T00:00:00Z", "2026-11-15T20:00:00Z"));
        }
    }
}
