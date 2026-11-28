package com.connecta.backend.analytics.service;

import com.connecta.backend.analytics.dto.ConversationAnalyticsResponse;
import com.connecta.backend.analytics.dto.ConversationAnalyticsResponse.ChannelCount;
import com.connecta.backend.analytics.dto.ConversationAnalyticsResponse.DayCount;
import com.connecta.backend.analytics.repository.ConversationAnalyticsRepository;
import com.connecta.backend.analytics.repository.ConversationAnalyticsRepository.FirstResponse;
import com.connecta.backend.analytics.repository.ConversationAnalyticsRepository.MessageCounts;
import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.org.service.ActiveCaller;
import com.connecta.backend.org.service.TenantAccessService;
import com.connecta.backend.user.domain.Roles;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Conversation metrics of one organization for a date range. ADMIN of the organization only (OWNER inherits). */
@Service
@RequiredArgsConstructor
public class ConversationAnalyticsService {

    private final ConversationAnalyticsRepository analytics;
    private final TenantAccessService tenants;
    private final ActiveCaller activeCaller;
    private final Clock clock;

    /** Null {@code from}, {@code to} or {@code zone} take the defaults of {@link ReportRange#of}. */
    @PreAuthorize("hasRole('" + Roles.ADMIN + "')")
    @Transactional(readOnly = true)
    public ConversationAnalyticsResponse conversations(String subject, UUID organizationId, LocalDate from,
            LocalDate to, String zone) {
        tenants.requireRole(organizationId, activeCaller.load(subject).getId(), Roles.ADMIN);
        ReportRange range = ReportRange.of(from, to, zone, clock);
        List<DayCount> perDay = analytics.startedPerDay(organizationId, range);
        MessageCounts messages = analytics.messageCounts(organizationId, range);
        FirstResponse firstResponse = analytics.firstResponse(organizationId, range);
        return new ConversationAnalyticsResponse(range.from(), range.to(), range.zone().getId(),
                perDay.stream().mapToLong(DayCount::count).sum(), perDay,
                everyChannel(analytics.startedPerChannel(organizationId, range)),
                analytics.statusNow(organizationId), messages.received(), messages.sent(),
                firstResponse.medianSeconds(), firstResponse.unanswered());
    }

    /** Every channel in a fixed order, 0 for one without conversations, so the list is always complete. */
    private static List<ChannelCount> everyChannel(Map<String, Long> counts) {
        return Arrays.stream(Channel.values())
                .map(channel -> new ChannelCount(channel, counts.getOrDefault(channel.name(), 0L)))
                .toList();
    }
}
