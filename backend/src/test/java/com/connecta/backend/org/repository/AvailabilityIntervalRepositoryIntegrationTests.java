package com.connecta.backend.org.repository;

import com.connecta.backend.support.SharedPostgres;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import org.springframework.transaction.support.TransactionTemplate;

/** Real PostgreSQL: the one-open-interval index, the open lookups and tenant scoping. Skipped without Docker. */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false"})
@Import(TestClock.class)
@ContextConfiguration(initializers = RandomJwtSecret.class)
class AvailabilityIntervalRepositoryIntegrationTests extends SharedPostgres {

    private static final Instant START = Instant.parse("2026-11-15T10:00:00Z");

    @Autowired
    EntityManager entities;

    @Autowired
    TransactionTemplate transactions;

    @Autowired
    RoleRepository roles;

    @Autowired
    AvailabilityIntervalRepository intervals;

    @Test
    void aMemberCannotHaveTwoOpenIntervals() {
        Membership member = newMember(newOrganization());
        persist(new AvailabilityInterval(member, START));

        assertThrows(RuntimeException.class, () -> persist(new AvailabilityInterval(member, START.plusSeconds(5))));
    }

    @Test
    void aClosedIntervalAllowsANewOpenOne() {
        Membership member = newMember(newOrganization());
        AvailabilityInterval first = new AvailabilityInterval(member, START);
        first.close(START.plusSeconds(60));
        persist(first);
        persist(new AvailabilityInterval(member, START.plusSeconds(120)));

        assertTrue(intervals.findByMembershipIdAndOrganizationIdAndEndedAtIsNull(
                member.getId(), member.getOrganization().getId()).isPresent());
    }

    @Test
    void anOpenIntervalIsFoundOnlyInsideItsOrganization() {
        Organization mine = newOrganization();
        Membership member = newMember(mine);
        persist(new AvailabilityInterval(member, START));

        assertTrue(intervals.findByMembershipIdAndOrganizationIdAndEndedAtIsNull(
                member.getId(), mine.getId()).isPresent());
        assertTrue(intervals.findByMembershipIdAndOrganizationIdAndEndedAtIsNull(
                member.getId(), newOrganization().getId()).isEmpty());
    }

    @Test
    void openIntervalsOfAUserSpanTheirMembershipsButNotOthers() {
        User user = newUser();
        persist(new AvailabilityInterval(newMember(newOrganization(), user), START));
        persist(new AvailabilityInterval(newMember(newOrganization(), user), START));
        persist(new AvailabilityInterval(newMember(newOrganization()), START));

        int open = transactions.execute(status -> intervals.findByMembershipUserIdAndEndedAtIsNull(user.getId())
                .size());
        assertEquals(2, open);
    }

    @Test
    void overlappingFindsOnlyIntervalsTouchingTheRangeInsideTheOrganization() {
        Organization mine = newOrganization();
        Membership member = newMember(mine);
        Instant from = START;
        Instant to = START.plusSeconds(3600);
        persist(closed(member, from.minusSeconds(600), from.plusSeconds(60)));
        persist(closed(member, from.minusSeconds(7200), from.minusSeconds(3600)));
        persist(closed(member, to, to.plusSeconds(60)));
        persist(new AvailabilityInterval(member, to.minusSeconds(60)));

        List<AvailabilityInterval> found = transactions.execute(status ->
                intervals.findOverlapping(mine.getId(), List.of(member.getId()), from, to).stream()
                        .peek(i -> i.getMembership().getId()).toList());
        List<AvailabilityInterval> elsewhere =
                intervals.findOverlapping(newOrganization().getId(), List.of(member.getId()), from, to);

        assertEquals(2, found.size());
        assertTrue(elsewhere.isEmpty());
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

    private User newUser() {
        return persist(new User("u" + UUID.randomUUID() + "@example.com", "hash", "Agent"));
    }

    private Membership newMember(Organization org) {
        return newMember(org, newUser());
    }

    private Membership newMember(Organization org, User user) {
        return persist(new Membership(org, user, roles.findByName("AGENT").orElseThrow()));
    }
}
