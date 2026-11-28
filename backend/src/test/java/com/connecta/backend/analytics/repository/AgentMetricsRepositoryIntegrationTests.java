package com.connecta.backend.analytics.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.connecta.backend.support.RandomJwtSecret;
import com.connecta.backend.support.SharedPostgres;
import com.connecta.backend.support.TestClock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ContextConfiguration;

/**
 * The agent metrics queries against real PostgreSQL. Each test builds its own organizations, so rows other
 * tests left in the shared database never count. The range is one week; {@code now} is two days after it.
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false"})
@Import(TestClock.class)
@ContextConfiguration(initializers = RandomJwtSecret.class)
class AgentMetricsRepositoryIntegrationTests extends SharedPostgres {

    private static final Instant START = Instant.parse("2026-11-01T00:00:00Z");
    private static final Instant END = Instant.parse("2026-11-08T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-11-10T00:00:00Z");

    @Autowired
    AgentMetricsRepository repository;

    @Autowired
    JdbcClient jdbc;

    private UUID org;
    private UUID otherOrg;
    private UUID aliceUser;
    private UUID bobUser;
    private UUID alice;
    private UUID bob;
    private UUID carl;

    @BeforeEach
    void seed() {
        org = organization();
        otherOrg = organization();
        aliceUser = user("Alice");
        bobUser = user("Bob");
        carl = membership(org, user("Carl"), "AGENT");
        bob = membership(org, bobUser, "ADMIN");
        alice = membership(org, aliceUser, "AGENT");
        membership(otherOrg, aliceUser, "AGENT");
    }

    private AgentMetricsRow row(UUID membershipId) {
        return rows(END, NOW).stream().filter(r -> r.membershipId().equals(membershipId)).findFirst().orElseThrow();
    }

    private List<AgentMetricsRow> rows(Instant end, Instant now) {
        return repository.findAgentMetrics(org, START, end, now, PageRequest.of(0, 20)).getContent();
    }

    @Nested
    class Members {

        @Test
        void everyMemberOfTheOrganizationHasARowSortedByName() {
            List<AgentMetricsRow> rows = rows(END, NOW);

            assertEquals(List.of(alice, bob, carl), rows.stream().map(AgentMetricsRow::membershipId).toList());
            assertEquals(List.of("Alice", "Bob", "Carl"), rows.stream().map(AgentMetricsRow::name).toList());
            assertEquals(List.of("AGENT", "ADMIN", "AGENT"), rows.stream().map(AgentMetricsRow::role).toList());
        }

        @Test
        void aMemberWithNoActivityHasZerosAndNoMedian() {
            AgentMetricsRow row = row(carl);

            assertEquals(new AgentMetricsRow(carl, "Carl", "AGENT", 0, 0, 0, 0, 0, null), row);
        }

        @Test
        void aPageKeepsTheNameOrderAndTheTotal() {
            Page<AgentMetricsRow> second = repository.findAgentMetrics(org, START, END, NOW, PageRequest.of(1, 2));

            assertEquals(3, second.getTotalElements());
            assertEquals(List.of(carl), second.getContent().stream().map(AgentMetricsRow::membershipId).toList());
        }
    }

    @Nested
    class Counts {

        @Test
        void assignedCountsHandOversToTheMemberInsideTheRange() {
            UUID c = conversation(org, "OPEN", null);
            assignment(c, alice, START);
            assignment(c, alice, END.minusSeconds(1));
            assignment(c, alice, START.minusSeconds(1));
            assignment(c, alice, END);
            assignment(c, bob, START.plusSeconds(1));

            assertEquals(2, row(alice).assigned());
        }

        @Test
        void resolvedCountsConversationsTheUserClosedInRangeInThisOrganization() {
            close(conversation(org, "CLOSED", null), aliceUser, START.plusSeconds(60));
            close(conversation(org, "CLOSED", null), aliceUser, START.minusSeconds(60));
            close(conversation(otherOrg, "CLOSED", null), aliceUser, START.plusSeconds(60));

            assertEquals(1, row(alice).resolved());
        }

        @Test
        void openNowCountsOpenConversationsAssignedToTheMember() {
            conversation(org, "OPEN", alice);
            conversation(org, "OPEN", alice);
            conversation(org, "CLOSED", alice);
            conversation(org, "OPEN", bob);

            assertEquals(2, row(alice).openNow());
        }

        @Test
        void repliesSentCountsTheUsersOutboundMessagesInRangeInThisOrganization() {
            UUID c = conversation(org, "OPEN", null);
            outbound(org, c, aliceUser, START.plusSeconds(10));
            outbound(org, c, aliceUser, END.minusSeconds(10));
            outbound(org, c, aliceUser, START.minusSeconds(10));
            outbound(org, c, null, START.plusSeconds(20));
            outbound(otherOrg, conversation(otherOrg, "OPEN", null), aliceUser, START.plusSeconds(10));

            assertEquals(2, row(alice).repliesSent());
        }
    }

    @Nested
    class AvailableTime {

        @Test
        void onlyTheOverlapWithTheRangeCounts() {
            interval(alice, START.minus(Duration.ofDays(1)), START.plus(Duration.ofHours(1)));
            interval(alice, START.plus(Duration.ofDays(2)), START.plus(Duration.ofDays(2)).plusSeconds(600));
            interval(alice, START.minus(Duration.ofDays(3)), START.minus(Duration.ofDays(2)));

            assertEquals(3600 + 600, row(alice).availableSeconds());
        }

        @Test
        void anOpenIntervalIsCutAtTheRangeEnd() {
            interval(alice, END.minus(Duration.ofMinutes(30)), null);

            assertEquals(1800, row(alice).availableSeconds());
        }

        @Test
        void anOpenIntervalCountsUpToNowWhenTheRangeRunsPastIt() {
            Instant now = END.minus(Duration.ofMinutes(10));
            interval(alice, END.minus(Duration.ofMinutes(30)), null);

            assertEquals(1200, rows(END, now).get(0).availableSeconds());
        }
    }

    @Nested
    class MedianFirstResponse {

        @Test
        void isTheMedianOverConversationsWhoseFirstReplyCameFromTheMember() {
            firstExchange(aliceUser, START.plusSeconds(3600), 600);
            firstExchange(aliceUser, START.plusSeconds(7200), 1200);
            firstExchange(bobUser, START.plusSeconds(9000), 300);

            assertEquals(900, row(alice).medianFirstResponseSeconds());
            assertEquals(300, row(bob).medianFirstResponseSeconds());
        }

        @Test
        void aConversationStartedBeforeTheRangeIsNotMeasured() {
            firstExchange(aliceUser, START.minusSeconds(60), 600);

            assertNull(row(alice).medianFirstResponseSeconds());
        }

        @Test
        void onlyTheFirstReplyAfterTheFirstInboundMessageCounts() {
            UUID c = conversation(org, "OPEN", null);
            inbound(c, START.plusSeconds(100));
            outbound(org, c, bobUser, START.plusSeconds(400));
            outbound(org, c, aliceUser, START.plusSeconds(500));

            assertNull(row(alice).medianFirstResponseSeconds());
            assertEquals(300, row(bob).medianFirstResponseSeconds());
        }
    }

    private void firstExchange(UUID replier, Instant firstInbound, long replyAfterSeconds) {
        UUID c = conversation(org, "OPEN", null);
        inbound(c, firstInbound);
        outbound(org, c, replier, firstInbound.plusSeconds(replyAfterSeconds));
    }

    private UUID organization() {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO organizations (id, name, slug) VALUES (?, 'Org', ?)").params(id, "org-" + id).update();
        return id;
    }

    private UUID user(String name) {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO users (id, email, password_hash, full_name) VALUES (?, ?, 'hash', ?)")
                .params(id, id + "@example.com", name).update();
        return id;
    }

    private UUID membership(UUID organization, UUID user, String role) {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO memberships (id, organization_id, user_id, role_id) SELECT ?, ?, ?, id FROM roles "
                + "WHERE name = ?").params(id, organization, user, role).update();
        return id;
    }

    private UUID conversation(UUID organization, String status, UUID assignee) {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO conversations (id, organization_id, channel, external_thread_id, status, "
                        + "assignee_membership_id) VALUES (?, ?, 'MESSENGER', ?, ?, ?)")
                .params(id, organization, "thread-" + id, status, assignee).update();
        return id;
    }

    private void close(UUID conversation, UUID user, Instant at) {
        jdbc.sql("UPDATE conversations SET closed_by_user_id = ?, closed_at = ? WHERE id = ?")
                .params(user, utc(at), conversation).update();
    }

    private void assignment(UUID conversation, UUID to, Instant at) {
        jdbc.sql("INSERT INTO conversation_assignments (organization_id, conversation_id, to_membership_id, source, "
                + "created_at) VALUES (?, ?, ?, 'MANUAL', ?)").params(org, conversation, to, utc(at)).update();
    }

    private void inbound(UUID conversation, Instant at) {
        UUID customer = UUID.randomUUID();
        jdbc.sql("INSERT INTO participants (id, organization_id, channel, external_id) VALUES (?, ?, 'MESSENGER', ?)")
                .params(customer, org, "psid-" + customer).update();
        jdbc.sql("INSERT INTO messages (organization_id, conversation_id, direction, sender_participant_id, body, "
                + "sent_at) VALUES (?, ?, 'INBOUND', ?, 'hi', ?)").params(org, conversation, customer, utc(at))
                .update();
    }

    private void outbound(UUID organization, UUID conversation, UUID sentBy, Instant at) {
        jdbc.sql("INSERT INTO messages (organization_id, conversation_id, direction, sent_by_user_id, body, sent_at) "
                + "VALUES (?, ?, 'OUTBOUND', ?, 'hello', ?)").params(organization, conversation, sentBy, utc(at))
                .update();
    }

    private void interval(UUID membership, Instant startedAt, Instant endedAt) {
        jdbc.sql("INSERT INTO availability_intervals (organization_id, membership_id, started_at, ended_at) "
                        + "VALUES (?, ?, ?, ?)")
                .params(org, membership, utc(startedAt), endedAt == null ? null : utc(endedAt)).update();
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
