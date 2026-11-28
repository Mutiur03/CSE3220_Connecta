package com.connecta.backend.analytics.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.connecta.backend.analytics.dto.AgentAnalyticsResponse;
import com.connecta.backend.analytics.dto.AgentAnalyticsResponse.AgentMetrics;
import com.connecta.backend.analytics.repository.AgentMetricsRepository;
import com.connecta.backend.analytics.repository.AgentMetricsRow;
import com.connecta.backend.org.service.ActiveCaller;
import com.connecta.backend.org.service.InsufficientOrganizationRoleException;
import com.connecta.backend.org.service.NotAMemberException;
import com.connecta.backend.org.service.TenantAccessService;
import com.connecta.backend.support.TestClock;
import com.connecta.backend.support.TestUsers;
import com.connecta.backend.user.domain.Roles;
import com.connecta.backend.user.domain.User;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class AgentAnalyticsServiceTests {

    @Mock
    AgentMetricsRepository metrics;

    @Mock
    TenantAccessService tenants;

    @Mock
    ActiveCaller activeCaller;

    AgentAnalyticsService service;

    private final UUID organizationId = UUID.randomUUID();
    private final User caller = TestUsers.user("boss@example.com", Roles.ADMIN);
    private final String subject = caller.getId().toString();
    private final LocalDate from = LocalDate.parse("2026-11-01");
    private final LocalDate to = LocalDate.parse("2026-11-07");

    @BeforeEach
    void setUp() {
        service = new AgentAnalyticsService(metrics, tenants, activeCaller, TestClock.FIXED);
        when(activeCaller.load(subject)).thenReturn(caller);
    }

    @Nested
    class Agents {

        @Test
        void mapsOnePageOfRowsWithTheRangeAndPagingFacts() {
            AgentMetricsRow row = new AgentMetricsRow(UUID.randomUUID(), "Aavas", "AGENT", 48, 41, 6, 312, 302400,
                    420L);
            ReportRange range = new ReportRange(from, to, ZoneId.of("Asia/Dhaka"));
            PageRequest page = PageRequest.of(1, 5);
            when(metrics.findAgentMetrics(organizationId, range, TestClock.NOW, page))
                    .thenReturn(new PageImpl<>(List.of(row), page, 6));

            AgentAnalyticsResponse report = service.agents(subject, organizationId, from, to, "Asia/Dhaka", 1, 5);

            assertEquals(from, report.from());
            assertEquals(to, report.to());
            assertEquals("Asia/Dhaka", report.zone());
            assertEquals(1, report.page());
            assertEquals(5, report.size());
            assertEquals(6, report.totalElements());
            assertEquals(List.of(new AgentMetrics(row.membershipId(), "Aavas", "AGENT", 48, 41, 6, 312, 302400, 420L)),
                    report.content());
        }

        @Test
        void noDatesOrZoneMeansTheLastThirtyDaysInUtc() {
            ReportRange expected = new ReportRange(LocalDate.parse("2026-10-17"), LocalDate.parse("2026-11-15"),
                    ReportRange.DEFAULT_ZONE);
            when(metrics.findAgentMetrics(eq(organizationId), eq(expected), eq(TestClock.NOW), any()))
                    .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

            AgentAnalyticsResponse report = service.agents(subject, organizationId, null, null, null, 0, 20);

            assertEquals(expected.from(), report.from());
            assertEquals(expected.to(), report.to());
            assertEquals("UTC", report.zone());
            assertEquals(List.of(), report.content());
        }

        @Test
        void aMemberWhoIsNotAnAdminIsRefusedBeforeAnyQuery() {
            when(tenants.requireRole(organizationId, caller.getId(), Roles.ADMIN))
                    .thenThrow(new InsufficientOrganizationRoleException());

            assertThrows(InsufficientOrganizationRoleException.class,
                    () -> service.agents(subject, organizationId, from, to, null, 0, 20));
            verifyNoInteractions(metrics);
        }

        @Test
        void aNonMemberIsRefusedBeforeAnyQuery() {
            when(tenants.requireRole(organizationId, caller.getId(), Roles.ADMIN))
                    .thenThrow(new NotAMemberException());

            assertThrows(NotAMemberException.class,
                    () -> service.agents(subject, organizationId, from, to, null, 0, 20));
            verifyNoInteractions(metrics);
        }

        @Test
        void anInvalidRangeIsRefusedBeforeAnyQuery() {
            assertThrows(InvalidReportRangeException.class,
                    () -> service.agents(subject, organizationId, to, from, null, 0, 20));
            verifyNoInteractions(metrics);
        }
    }
}
