package com.connecta.backend.analytics.repository;

import com.connecta.backend.analytics.service.ReportRange;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Per-member metrics for the agent report, read straight from the inbox and availability tables in two
 * queries (one page of rows, one count). Every table is filtered by organization_id. The range is
 * {@code [start, end)}; {@code now} closes a still-open availability interval.
 */
@Repository
@RequiredArgsConstructor
public class AgentMetricsRepository {

    /*
     * One row per member on the page, sorted by name then id so paging is stable. "Started" matches the
     * conversation report: a message in range and none before it. first_replies holds, per started
     * conversation, the first outbound message after its first inbound one; the median is taken over
     * the rows sent by the member.
     */
    private static final String PAGE_SQL = """
            WITH members AS (
                SELECT m.id, m.user_id, u.full_name, r.name AS role
                FROM memberships m
                JOIN users u ON u.id = m.user_id
                JOIN roles r ON r.id = m.role_id
                WHERE m.organization_id = :organizationId
                ORDER BY u.full_name, m.id
                LIMIT :limit OFFSET :offset
            ),
            started AS (
                SELECT m.conversation_id
                FROM messages m
                WHERE m.organization_id = :organizationId AND m.sent_at >= :start AND m.sent_at < :end
                GROUP BY m.conversation_id
                HAVING NOT EXISTS (
                    SELECT 1 FROM messages e
                    WHERE e.organization_id = :organizationId AND e.conversation_id = m.conversation_id
                      AND e.sent_at < :start)
            ),
            first_inbound AS (
                SELECT s.conversation_id,
                       (SELECT MIN(i.sent_at) FROM messages i
                        WHERE i.organization_id = :organizationId AND i.conversation_id = s.conversation_id
                          AND i.direction = 'INBOUND') AS inbound_at
                FROM started s
            ),
            first_replies AS (
                SELECT reply.sent_by_user_id, EXTRACT(EPOCH FROM reply.sent_at - f.inbound_at) AS seconds
                FROM first_inbound f
                CROSS JOIN LATERAL (
                    SELECT o.sent_at, o.sent_by_user_id
                    FROM messages o
                    WHERE o.organization_id = :organizationId
                      AND o.conversation_id = f.conversation_id
                      AND o.direction = 'OUTBOUND'
                      AND o.sent_at > f.inbound_at
                    ORDER BY o.sent_at, o.id
                    LIMIT 1
                ) reply
                WHERE f.inbound_at IS NOT NULL
            )
            SELECT mb.id AS membership_id,
                   mb.full_name AS name,
                   mb.role,
                   (SELECT count(*) FROM conversation_assignments a
                    WHERE a.organization_id = :organizationId AND a.to_membership_id = mb.id
                      AND a.created_at >= :start AND a.created_at < :end) AS assigned,
                   (SELECT count(*) FROM conversations c
                    WHERE c.organization_id = :organizationId AND c.closed_by_user_id = mb.user_id
                      AND c.closed_at >= :start AND c.closed_at < :end) AS resolved,
                   (SELECT count(*) FROM conversations c
                    WHERE c.organization_id = :organizationId AND c.assignee_membership_id = mb.id
                      AND c.status = 'OPEN') AS open_now,
                   (SELECT count(*) FROM messages msg
                    WHERE msg.organization_id = :organizationId AND msg.sent_by_user_id = mb.user_id
                      AND msg.direction = 'OUTBOUND'
                      AND msg.sent_at >= :start AND msg.sent_at < :end) AS replies_sent,
                   (SELECT COALESCE(sum(GREATEST(EXTRACT(EPOCH FROM
                               LEAST(COALESCE(i.ended_at, :now), :end) - GREATEST(i.started_at, :start)), 0)),
                           0)::bigint
                    FROM availability_intervals i
                    WHERE i.organization_id = :organizationId AND i.membership_id = mb.id
                      AND i.started_at < :end AND COALESCE(i.ended_at, :now) > :start) AS available_seconds,
                   (SELECT round(percentile_cont(0.5) WITHIN GROUP (ORDER BY f.seconds))::bigint
                    FROM first_replies f
                    WHERE f.sent_by_user_id = mb.user_id) AS median_first_response_seconds
            FROM members mb
            ORDER BY mb.full_name, mb.id
            """;

    private static final String COUNT_SQL = "SELECT count(*) FROM memberships WHERE organization_id = :organizationId";

    private final JdbcClient jdbc;

    /** One page of the organization's members with their metrics for the range. */
    public Page<AgentMetricsRow> findAgentMetrics(UUID organizationId, ReportRange range, Instant now,
            Pageable pageable) {
        List<AgentMetricsRow> rows = jdbc.sql(PAGE_SQL)
                .param("organizationId", organizationId)
                .param("start", utc(range.start()))
                .param("end", utc(range.end()))
                .param("now", utc(now))
                .param("limit", pageable.getPageSize())
                .param("offset", pageable.getOffset())
                .query(AgentMetricsRow.class)
                .list();
        long total = jdbc.sql(COUNT_SQL).param("organizationId", organizationId).query(Long.class).single();
        return new PageImpl<>(rows, pageable, total);
    }

    /** The PostgreSQL driver binds OffsetDateTime as timestamptz; it has no mapping for Instant. */
    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
