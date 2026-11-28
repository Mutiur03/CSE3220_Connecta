package com.connecta.backend.analytics.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.connecta.backend.analytics.dto.ConversationAnalyticsResponse;
import com.connecta.backend.analytics.dto.ConversationAnalyticsResponse.ChannelCount;
import com.connecta.backend.analytics.dto.ConversationAnalyticsResponse.DayCount;
import com.connecta.backend.analytics.dto.ConversationAnalyticsResponse.StatusNow;
import com.connecta.backend.analytics.repository.ConversationAnalyticsRepository;
import com.connecta.backend.analytics.repository.ConversationAnalyticsRepository.FirstResponse;
import com.connecta.backend.analytics.repository.ConversationAnalyticsRepository.MessageCounts;
import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.org.service.ActiveCaller;
import com.connecta.backend.org.service.InsufficientOrganizationRoleException;
import com.connecta.backend.org.service.NotAMemberException;
import com.connecta.backend.org.service.TenantAccessService;
import com.connecta.backend.support.TestUsers;
import com.connecta.backend.user.domain.Roles;
import com.connecta.backend.user.domain.User;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConversationAnalyticsServiceTests {

    @Mock
    ConversationAnalyticsRepository analytics;

    @Mock
    TenantAccessService tenants;

    @Mock
    ActiveCaller activeCaller;

    ConversationAnalyticsService service;

    private final UUID organizationId = UUID.randomUUID();
    private final User caller = TestUsers.user("boss@example.com", Roles.ADMIN);
    private final String subject = caller.getId().toString();
    private final LocalDate from = LocalDate.parse("2026-11-01");
    private final LocalDate to = LocalDate.parse("2026-11-02");

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-15T12:00:00Z"), ZoneOffset.UTC);
        service = new ConversationAnalyticsService(analytics, tenants, activeCaller, clock);
        when(activeCaller.load(subject)).thenReturn(caller);
    }

    @Nested
    class Conversations {

        @Test
        void combinesTheAggregatesIntoOneReport() {
            when(analytics.startedPerDay(any(), any())).thenReturn(
                    List.of(new DayCount(from, 3), new DayCount(to, 2)));
            when(analytics.startedPerChannel(any(), any())).thenReturn(Map.of("INSTAGRAM", 5L));
            when(analytics.statusNow(organizationId)).thenReturn(new StatusNow(7, 9));
            when(analytics.messageCounts(any(), any())).thenReturn(new MessageCounts(40, 30));
            when(analytics.firstResponse(any(), any())).thenReturn(new FirstResponse(540L, 1));

            ConversationAnalyticsResponse report = service.conversations(subject, organizationId, from, to,
                    "Asia/Dhaka");

            assertEquals(from, report.from());
            assertEquals(to, report.to());
            assertEquals("Asia/Dhaka", report.zone());
            assertEquals(5, report.started());
            assertEquals(List.of(new ChannelCount(Channel.INSTAGRAM, 5), new ChannelCount(Channel.MESSENGER, 0)),
                    report.startedPerChannel());
            assertEquals(new StatusNow(7, 9), report.statusNow());
            assertEquals(40, report.messagesReceived());
            assertEquals(30, report.messagesSent());
            assertEquals(540L, report.medianFirstResponseSeconds());
            assertEquals(1, report.unanswered());
        }

        @Test
        void noRepliesLeavesTheMedianNull() {
            when(analytics.startedPerDay(any(), any())).thenReturn(List.of());
            when(analytics.startedPerChannel(any(), any())).thenReturn(Map.of());
            when(analytics.statusNow(organizationId)).thenReturn(new StatusNow(0, 0));
            when(analytics.messageCounts(any(), any())).thenReturn(new MessageCounts(0, 0));
            when(analytics.firstResponse(any(), any())).thenReturn(new FirstResponse(null, 0));

            ConversationAnalyticsResponse report = service.conversations(subject, organizationId, null, null, null);

            assertNull(report.medianFirstResponseSeconds());
            assertEquals(0, report.started());
            assertEquals("UTC", report.zone());
        }

        @Test
        void aMemberWhoIsNotAnAdminIsRefusedBeforeAnyQuery() {
            when(tenants.requireRole(organizationId, caller.getId(), Roles.ADMIN))
                    .thenThrow(new InsufficientOrganizationRoleException());

            assertThrows(InsufficientOrganizationRoleException.class,
                    () -> service.conversations(subject, organizationId, from, to, null));
            verifyNoInteractions(analytics);
        }

        @Test
        void aNonMemberIsRefusedBeforeAnyQuery() {
            when(tenants.requireRole(organizationId, caller.getId(), Roles.ADMIN))
                    .thenThrow(new NotAMemberException());

            assertThrows(NotAMemberException.class,
                    () -> service.conversations(subject, organizationId, from, to, null));
            verifyNoInteractions(analytics);
        }

        @Test
        void anInvalidRangeIsRefusedBeforeAnyQuery() {
            assertThrows(InvalidReportRangeException.class,
                    () -> service.conversations(subject, organizationId, to, from, null));
            verifyNoInteractions(analytics);
        }
    }
}
