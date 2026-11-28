package com.connecta.backend.analytics.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ReportRangeTests {

    /** 20:00 UTC on 15 November is already 16 November in Dhaka (UTC+6). */
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-15T20:00:00Z"), ZoneOffset.UTC);

    @Nested
    class Defaults {

        @Test
        void noValuesMeanTheLastThirtyDaysEndingTodayInUtc() {
            ReportRange range = ReportRange.of(null, null, null, clock);

            assertEquals(LocalDate.parse("2026-10-17"), range.from());
            assertEquals(LocalDate.parse("2026-11-15"), range.to());
            assertEquals(ZoneId.of("UTC"), range.zone());
        }

        @Test
        void todayIsReadInTheRequestedZone() {
            ReportRange range = ReportRange.of(null, null, "Asia/Dhaka", clock);

            assertEquals(LocalDate.parse("2026-11-16"), range.to());
            assertEquals(LocalDate.parse("2026-10-18"), range.from());
        }

        @Test
        void onlyToGivenEndsThirtyDaysThere() {
            ReportRange range = ReportRange.of(null, LocalDate.parse("2026-03-30"), "", clock);

            assertEquals(LocalDate.parse("2026-03-01"), range.from());
            assertEquals(ReportRange.DEFAULT_ZONE, range.zone());
        }

        @Test
        void onlyFromGivenRunsToToday() {
            ReportRange range = ReportRange.of(LocalDate.parse("2026-11-01"), null, null, clock);

            assertEquals(LocalDate.parse("2026-11-01"), range.from());
            assertEquals(LocalDate.parse("2026-11-15"), range.to());
        }
    }

    @Nested
    class Validation {

        @Test
        void fromAfterToIsRefused() {
            assertThrows(InvalidReportRangeException.class,
                    () -> ReportRange.of(LocalDate.parse("2026-11-02"), LocalDate.parse("2026-11-01"), null, clock));
        }

        @Test
        void aSingleDayIsAllowed() {
            LocalDate day = LocalDate.parse("2026-11-01");

            assertEquals(day, ReportRange.of(day, day, null, clock).to());
        }

        @Test
        void exactly366DaysIsAllowed() {
            ReportRange range = ReportRange.of(LocalDate.parse("2024-01-01"), LocalDate.parse("2024-12-31"), null,
                    clock);

            assertEquals(LocalDate.parse("2024-12-31"), range.to());
        }

        @Test
        void moreThan366DaysIsRefused() {
            assertThrows(InvalidReportRangeException.class,
                    () -> ReportRange.of(LocalDate.parse("2024-01-01"), LocalDate.parse("2025-01-01"), null, clock));
        }

        @Test
        void anUnknownZoneIsRefused() {
            assertThrows(InvalidReportRangeException.class, () -> ReportRange.of(null, null, "Mars/Olympus", clock));
        }

        @Test
        void anOffsetInsteadOfARegionIsRefused() {
            assertThrows(InvalidReportRangeException.class, () -> ReportRange.of(null, null, "+06:00", clock));
        }
    }

    @Nested
    class Boundaries {

        @Test
        void startAndEndAreTheDayEdgesInTheZone() {
            ReportRange range = new ReportRange(LocalDate.parse("2026-11-10"), LocalDate.parse("2026-11-12"),
                    ZoneId.of("Asia/Dhaka"));

            assertEquals(Instant.parse("2026-11-09T18:00:00Z"), range.start());
            assertEquals(Instant.parse("2026-11-12T18:00:00Z"), range.end());
        }

        @Test
        void aDaylightSavingDayFollowsTheZone() {
            ReportRange range = new ReportRange(LocalDate.parse("2026-03-08"), LocalDate.parse("2026-03-08"),
                    ZoneId.of("America/New_York"));

            assertEquals(Instant.parse("2026-03-08T05:00:00Z"), range.start());
            assertEquals(Instant.parse("2026-03-09T04:00:00Z"), range.end());
        }
    }
}
