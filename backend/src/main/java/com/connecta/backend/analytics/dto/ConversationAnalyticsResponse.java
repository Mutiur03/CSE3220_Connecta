package com.connecta.backend.analytics.dto;

import com.connecta.backend.inbox.domain.Channel;
import java.time.LocalDate;
import java.util.List;

/** Conversation metrics for one range, as defined in docs/analytics-metrics.md. Durations are whole seconds. */
public record ConversationAnalyticsResponse(
        LocalDate from,
        LocalDate to,
        String zone,
        long started,
        List<DayCount> startedPerDay,
        List<ChannelCount> startedPerChannel,
        StatusNow statusNow,
        long messagesReceived,
        long messagesSent,
        Long medianFirstResponseSeconds,
        long unanswered) {

    public record DayCount(LocalDate date, long count) {
    }

    public record ChannelCount(Channel channel, long count) {
    }

    public record StatusNow(long open, long closed) {
    }
}
