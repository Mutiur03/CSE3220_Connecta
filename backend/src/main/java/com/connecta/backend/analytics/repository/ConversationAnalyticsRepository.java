package com.connecta.backend.analytics.repository;

import com.connecta.backend.analytics.dto.ConversationAnalyticsResponse.DayCount;
import com.connecta.backend.analytics.dto.ConversationAnalyticsResponse.StatusNow;
import com.connecta.backend.analytics.service.ReportRange;
import com.connecta.backend.inbox.domain.ConversationStatus;
import com.connecta.backend.inbox.domain.MessageDirection;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Read-only aggregates over the inbox tables, computed in PostgreSQL. Every query is filtered by organization.
 * A conversation "started" in the range when its first message (by {@code sent_at}) falls inside it, not when
 * the row was created, since a history import creates months of conversations at once.
 */
@Repository
@RequiredArgsConstructor
public class ConversationAnalyticsRepository {

    /** Conversations with a message in the range and none before it, with the time of that first message. */
    private static final String STARTED = """
            WITH started AS (
                SELECT m.conversation_id, MIN(m.sent_at) AS started_at
                FROM messages m
                WHERE m.organization_id = :organizationId AND m.sent_at >= :start AND m.sent_at < :end
                GROUP BY m.conversation_id
                HAVING NOT EXISTS (
                    SELECT 1 FROM messages e
                    WHERE e.organization_id = :organizationId AND e.conversation_id = m.conversation_id
                      AND e.sent_at < :start)
            )
            """;

    private static final String STARTED_PER_DAY = STARTED + """
            SELECT CAST(d AS date) AS day, COUNT(s.conversation_id) AS started
            FROM generate_series(CAST(:from AS date), CAST(:to AS date), INTERVAL '1 day') AS d
            LEFT JOIN started s ON CAST(s.started_at AT TIME ZONE :zone AS date) = CAST(d AS date)
            GROUP BY d
            ORDER BY d
            """;

    private static final String STARTED_PER_CHANNEL = STARTED + """
            SELECT c.channel, COUNT(*) AS started
            FROM started s
            JOIN conversations c ON c.id = s.conversation_id AND c.organization_id = :organizationId
            GROUP BY c.channel
            """;

    private static final String STATUS_NOW = """
            SELECT COUNT(*) FILTER (WHERE status = :open) AS open, COUNT(*) FILTER (WHERE status = :closed) AS closed
            FROM conversations
            WHERE organization_id = :organizationId
            """;

    private static final String MESSAGE_COUNTS = """
            SELECT COUNT(*) FILTER (WHERE direction = :inbound) AS received,
                   COUNT(*) FILTER (WHERE direction = :outbound) AS sent
            FROM messages
            WHERE organization_id = :organizationId AND sent_at >= :start AND sent_at < :end
            """;

    /** Per started conversation: its first inbound message and the first outbound one after it. */
    private static final String FIRST_RESPONSE = STARTED + """
            , first_inbound AS (
                SELECT s.conversation_id,
                       (SELECT MIN(i.sent_at) FROM messages i
                        WHERE i.organization_id = :organizationId AND i.conversation_id = s.conversation_id
                          AND i.direction = :inbound) AS inbound_at
                FROM started s
            ), replies AS (
                SELECT f.inbound_at,
                       (SELECT MIN(o.sent_at) FROM messages o
                        WHERE o.organization_id = :organizationId AND o.conversation_id = f.conversation_id
                          AND o.direction = :outbound AND o.sent_at > f.inbound_at) AS reply_at
                FROM first_inbound f
                WHERE f.inbound_at IS NOT NULL
            )
            SELECT CAST(ROUND(PERCENTILE_CONT(0.5) WITHIN GROUP (
                       ORDER BY EXTRACT(EPOCH FROM reply_at - inbound_at))) AS bigint) AS median_seconds,
                   COUNT(*) FILTER (WHERE reply_at IS NULL) AS unanswered
            FROM replies
            """;

    private final NamedParameterJdbcTemplate jdbc;

    /** One row per day of the range, oldest first, days without a started conversation included with 0. */
    public List<DayCount> startedPerDay(UUID organizationId, ReportRange range) {
        return jdbc.query(STARTED_PER_DAY, rangeParams(organizationId, range),
                (rs, row) -> new DayCount(rs.getObject("day", LocalDate.class), rs.getLong("started")));
    }

    /** Started conversations by channel name; channels without any are absent. */
    public Map<String, Long> startedPerChannel(UUID organizationId, ReportRange range) {
        Map<String, Long> counts = new LinkedHashMap<>();
        jdbc.query(STARTED_PER_CHANNEL, rangeParams(organizationId, range),
                rs -> { counts.put(rs.getString("channel"), rs.getLong("started")); });
        return counts;
    }

    /** Every conversation of the organization by current status, whatever the range. */
    public StatusNow statusNow(UUID organizationId) {
        MapSqlParameterSource params = new MapSqlParameterSource("organizationId", organizationId)
                .addValue("open", ConversationStatus.OPEN.name())
                .addValue("closed", ConversationStatus.CLOSED.name());
        return jdbc.queryForObject(STATUS_NOW, params,
                (rs, row) -> new StatusNow(rs.getLong("open"), rs.getLong("closed")));
    }

    /** Inbound and outbound messages sent in the range. */
    public MessageCounts messageCounts(UUID organizationId, ReportRange range) {
        return jdbc.queryForObject(MESSAGE_COUNTS, rangeParams(organizationId, range),
                (rs, row) -> new MessageCounts(rs.getLong("received"), rs.getLong("sent")));
    }

    /** Median first response over answered conversations started in the range, and how many got no reply. */
    public FirstResponse firstResponse(UUID organizationId, ReportRange range) {
        return jdbc.queryForObject(FIRST_RESPONSE, rangeParams(organizationId, range),
                (rs, row) -> new FirstResponse(rs.getObject("median_seconds", Long.class), rs.getLong("unanswered")));
    }

    private static MapSqlParameterSource rangeParams(UUID organizationId, ReportRange range) {
        return new MapSqlParameterSource("organizationId", organizationId)
                .addValue("start", OffsetDateTime.ofInstant(range.start(), ZoneOffset.UTC))
                .addValue("end", OffsetDateTime.ofInstant(range.end(), ZoneOffset.UTC))
                .addValue("from", range.from())
                .addValue("to", range.to())
                .addValue("zone", range.zone().getId())
                .addValue("inbound", MessageDirection.INBOUND.name())
                .addValue("outbound", MessageDirection.OUTBOUND.name());
    }

    public record MessageCounts(long received, long sent) {
    }

    /** {@code medianSeconds} is null when no conversation started in the range got a reply. */
    public record FirstResponse(Long medianSeconds, long unanswered) {
    }
}
