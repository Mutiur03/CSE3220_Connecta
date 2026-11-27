package com.connecta.backend.org.dto;

import com.connecta.backend.common.dto.PageResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** One page of members with the seconds each was AVAILABLE per UTC day; {@code days} covers every date. */
public record AvailabilityReportResponse(LocalDate from, LocalDate to, List<MemberReport> content, int page,
        int size, long totalElements) {

    public static AvailabilityReportResponse from(LocalDate from, LocalDate to, PageResponse<MemberReport> page) {
        return new AvailabilityReportResponse(from, to, page.content(), page.page(), page.size(),
                page.totalElements());
    }

    public record MemberReport(UUID membershipId, UUID userId, String name, String role, long totalSeconds,
            List<Day> days) {

        /** {@code perDay[i]} is the seconds on {@code from + i}. */
        public static MemberReport from(UUID membershipId, UUID userId, String name, String role,
                LocalDate from, long[] perDay) {
            List<Day> days = new ArrayList<>(perDay.length);
            long total = 0;
            for (int i = 0; i < perDay.length; i++) {
                days.add(new Day(from.plusDays(i), perDay[i]));
                total += perDay[i];
            }
            return new MemberReport(membershipId, userId, name, role, total, List.copyOf(days));
        }
    }

    public record Day(LocalDate date, long availableSeconds) {
    }
}
