package com.connecta.backend.analytics.repository;

import java.util.UUID;

/**
 * One member's numbers for a report range, as read from the database. Counts are never null;
 * {@code medianFirstResponseSeconds} is null when the member gave no first response in the range.
 */
public record AgentMetricsRow(
        UUID membershipId,
        String name,
        String role,
        long assigned,
        long resolved,
        long openNow,
        long repliesSent,
        long availableSeconds,
        Long medianFirstResponseSeconds) {
}
