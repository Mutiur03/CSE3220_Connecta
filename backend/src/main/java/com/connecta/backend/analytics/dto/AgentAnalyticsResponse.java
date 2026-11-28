package com.connecta.backend.analytics.dto;

import com.connecta.backend.analytics.repository.AgentMetricsRow;
import com.connecta.backend.analytics.service.ReportRange;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;

/** One page of members with their metrics for a range, as defined in docs/analytics-metrics.md. */
public record AgentAnalyticsResponse(
        LocalDate from,
        LocalDate to,
        String zone,
        List<AgentMetrics> content,
        int page,
        int size,
        long totalElements) {

    public static AgentAnalyticsResponse from(ReportRange range, Page<AgentMetricsRow> rows) {
        return new AgentAnalyticsResponse(range.from(), range.to(), range.zone().getId(),
                rows.getContent().stream().map(AgentMetrics::from).toList(), rows.getNumber(), rows.getSize(),
                rows.getTotalElements());
    }

    /** Counts are never null; {@code medianFirstResponseSeconds} is null when there is nothing to measure. */
    public record AgentMetrics(
            UUID membershipId,
            String name,
            String role,
            long assigned,
            long resolved,
            long openNow,
            long repliesSent,
            long availableSeconds,
            Long medianFirstResponseSeconds) {

        static AgentMetrics from(AgentMetricsRow row) {
            return new AgentMetrics(row.membershipId(), row.name(), row.role(), row.assigned(), row.resolved(),
                    row.openNow(), row.repliesSent(), row.availableSeconds(), row.medianFirstResponseSeconds());
        }
    }
}
