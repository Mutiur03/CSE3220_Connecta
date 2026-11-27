package com.connecta.backend.org.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/** Pure clipping math: how many seconds of one interval fall on each UTC day of a range. */
final class DailyAvailability {

    private static final long SECONDS_PER_DAY = 24 * 60 * 60;

    private DailyAvailability() {
    }

    /**
     * Adds the seconds of [start, end) to {@code perDay}, index 0 being {@code from}. {@code end} null means
     * still open. Nothing after {@code now} counts, and each day's part is whole seconds.
     */
    static void add(long[] perDay, LocalDate from, Instant start, Instant end, Instant now) {
        Instant stop = end == null || end.isAfter(now) ? now : end;
        for (int i = 0; i < perDay.length; i++) {
            Instant dayStart = from.plusDays(i).atStartOfDay().toInstant(ZoneOffset.UTC);
            Instant dayEnd = dayStart.plusSeconds(SECONDS_PER_DAY);
            Instant clippedStart = start.isAfter(dayStart) ? start : dayStart;
            Instant clippedEnd = stop.isBefore(dayEnd) ? stop : dayEnd;
            if (clippedEnd.isAfter(clippedStart)) {
                perDay[i] += clippedEnd.getEpochSecond() - clippedStart.getEpochSecond();
            }
        }
    }
}
