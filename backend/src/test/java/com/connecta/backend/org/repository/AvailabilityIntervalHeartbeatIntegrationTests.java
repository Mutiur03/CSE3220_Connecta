package com.connecta.backend.org.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.connecta.backend.org.domain.AvailabilityInterval;
import com.connecta.backend.org.domain.Membership;
import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.support.RandomJwtSecret;
import com.connecta.backend.support.TestClock;
import com.connecta.backend.user.domain.User;
import com.connecta.backend.user.repository.RoleRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Real PostgreSQL: the heartbeat bulk update and the startup close at last_seen_at. Skipped without Docker. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false"})
@Import(TestClock.class)
@ContextConfiguration(initializers = RandomJwtSecret.class)
class AvailabilityIntervalHeartbeatIntegrationTests {

    private static final Instant START = Instant.parse("2026-11-15T10:00:00Z");
    private static final Instant SEEN = START.plusSeconds(30);

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    EntityManager entities;

    @Autowired
    TransactionTemplate transactions;

    @Autowired
    RoleRepository roles;

    @Autowired
    AvailabilityIntervalRepository intervals;

    @Test
    void touchOpenStampsOnlyTheOpenIntervalsOfTheGivenMembers() {
        Organization org = newOrganization();
        Membership online = newMember(org);
        Membership otherInSameOrg = newMember(org);
        Membership otherOrg = newMember(newOrganization());
        AvailabilityInterval open = persist(new AvailabilityInterval(online, START));
        AvailabilityInterval closed = persist(closed(online, START.minusSeconds(600), START.minusSeconds(300)));
        AvailabilityInterval untouched = persist(new AvailabilityInterval(otherInSameOrg, START));
        AvailabilityInterval elsewhere = persist(new AvailabilityInterval(otherOrg, START));

        int updated = transactions.execute(status -> intervals.touchOpen(List.of(online.getId()), SEEN));

        assertEquals(1, updated);
        assertEquals(SEEN, reload(open).getLastSeenAt());
        assertEquals(START.minusSeconds(600), reload(closed).getLastSeenAt());
        assertEquals(START, reload(untouched).getLastSeenAt());
        assertEquals(START, reload(elsewhere).getLastSeenAt());
    }

    @Test
    void closeAllOpenAtLastSeenEndsAtTheHeartbeatAndLeavesClosedRowsAlone() {
        Membership beat = newMember(newOrganization());
        Membership neverBeat = newMember(newOrganization());
        Membership done = newMember(newOrganization());
        AvailabilityInterval stamped = persist(new AvailabilityInterval(beat, START));
        AvailabilityInterval fresh = persist(new AvailabilityInterval(neverBeat, START));
        AvailabilityInterval finished = persist(closed(done, START, START.plusSeconds(10)));
        transactions.executeWithoutResult(status -> intervals.touchOpen(List.of(beat.getId()), SEEN));

        transactions.executeWithoutResult(status -> intervals.closeAllOpenAtLastSeen());

        assertEquals(SEEN, reload(stamped).getEndedAt());
        assertEquals(START, reload(fresh).getEndedAt());
        assertEquals(START.plusSeconds(10), reload(finished).getEndedAt());
    }

    @Test
    void aRowWithoutLastSeenIsClosedAtItsStart() {
        Membership legacy = newMember(newOrganization());
        AvailabilityInterval open = persist(new AvailabilityInterval(legacy, START));
        transactions.executeWithoutResult(status -> entities.createNativeQuery(
                "update availability_intervals set last_seen_at = null where id = :id")
                .setParameter("id", open.getId()).executeUpdate());

        transactions.executeWithoutResult(status -> intervals.closeAllOpenAtLastSeen());

        assertEquals(START, reload(open).getEndedAt());
    }

    private AvailabilityInterval reload(AvailabilityInterval interval) {
        return transactions.execute(status -> {
            entities.clear();
            return entities.find(AvailabilityInterval.class, interval.getId());
        });
    }

    private static AvailabilityInterval closed(Membership member, Instant start, Instant end) {
        AvailabilityInterval interval = new AvailabilityInterval(member, start);
        interval.close(end);
        return interval;
    }

    private <T> T persist(T entity) {
        return transactions.execute(status -> {
            entities.persist(entity);
            entities.flush();
            return entity;
        });
    }

    private Organization newOrganization() {
        return persist(new Organization("Org " + UUID.randomUUID(), "org-" + UUID.randomUUID()));
    }

    private Membership newMember(Organization org) {
        User user = persist(new User("u" + UUID.randomUUID() + "@example.com", "hash", "Agent"));
        return persist(new Membership(org, user, roles.findByName("AGENT").orElseThrow()));
    }
}
