package com.connecta.backend.analytics.service;

import com.connecta.backend.analytics.dto.AgentAnalyticsResponse;
import com.connecta.backend.analytics.repository.AgentMetricsRepository;
import com.connecta.backend.org.service.ActiveCaller;
import com.connecta.backend.org.service.TenantAccessService;
import com.connecta.backend.user.domain.Roles;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Per-member metrics of one organization for a date range. ADMIN of the organization only (OWNER inherits). */
@Service
@RequiredArgsConstructor
public class AgentAnalyticsService {

    private final AgentMetricsRepository metrics;
    private final TenantAccessService tenants;
    private final ActiveCaller activeCaller;
    private final Clock clock;

    /** Null {@code from}, {@code to} or {@code zone} take the defaults of {@link ReportRange#of}. */
    @PreAuthorize("hasRole('" + Roles.ADMIN + "')")
    @Transactional(readOnly = true)
    public AgentAnalyticsResponse agents(String subject, UUID organizationId, LocalDate from, LocalDate to,
            String zone, int page, int size) {
        tenants.requireRole(organizationId, activeCaller.load(subject).getId(), Roles.ADMIN);
        ReportRange range = ReportRange.of(from, to, zone, clock);
        return AgentAnalyticsResponse.from(range,
                metrics.findAgentMetrics(organizationId, range, clock.instant(), PageRequest.of(page, size)));
    }
}
