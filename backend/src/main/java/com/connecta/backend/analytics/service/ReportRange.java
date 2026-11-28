package com.connecta.backend.analytics.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * The days a report covers: {@code from} and {@code to} inclusive, read in {@code zone}. Always valid once built:
 * {@code from} is not after {@code to} and the range is at most {@value #MAX_DAYS} days.
 */
public record ReportRange(LocalDate from, LocalDate to, ZoneId zone) {

    public static final int DEFAULT_DAYS = 30;
    public static final int MAX_DAYS = 366;
    public static final ZoneId DEFAULT_ZONE = ZoneId.of("UTC");

    public ReportRange {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(zone, "zone");
        if (from.isAfter(to)) {
            throw new InvalidReportRangeException("'from' must not be after 'to'");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
            throw new InvalidReportRangeException("The range must not be longer than " + MAX_DAYS + " days");
        }
    }

    /**
     * Builds a range from optional request values. No zone means UTC; no {@code to} means today in that zone; no
     * {@code from} means the {@value #DEFAULT_DAYS} days ending on {@code to}. A zone must be an IANA region id.
     */
    public static ReportRange of(LocalDate from, LocalDate to, String zone, Clock clock) {
        ZoneId zoneId = parseZone(zone);
        LocalDate end = to != null ? to : LocalDate.now(clock.withZone(zoneId));
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_DAYS - 1L);
        return new ReportRange(start, end, zoneId);
    }

    /** The first instant of {@code from} in the zone. */
    public Instant start() {
        return from.atStartOfDay(zone).toInstant();
    }

    /** The first instant after {@code to} in the zone (exclusive end). */
    public Instant end() {
        return to.plusDays(1).atStartOfDay(zone).toInstant();
    }

    /**
     * Only named IANA zones: an offset such as {@code +06:00} would be read with the opposite sign by PostgreSQL's
     * POSIX rules, so it is refused rather than silently shifting every day.
     */
    private static ZoneId parseZone(String zone) {
        if (zone == null || zone.isBlank()) {
            return DEFAULT_ZONE;
        }
        if (!ZoneId.getAvailableZoneIds().contains(zone)) {
            throw new InvalidReportRangeException("Unknown time zone: use an IANA zone id such as Asia/Dhaka");
        }
        return ZoneId.of(zone);
    }
}
