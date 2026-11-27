package com.connecta.backend.org.repository;

import com.connecta.backend.support.SharedPostgres;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.connecta.backend.org.domain.AgentAvailability;
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

/** Real PostgreSQL: the round-robin selection query. Skipped without Docker. */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false"})
@Import(TestClock.class)
@ContextConfiguration(initializers = RandomJwtSecret.class)
class AgentAvailabilityRepositoryIntegrationTests extends SharedPostgres {

    @Autowired
    EntityManager entities;

    @Autowired
    TransactionTemplate transactions;

    @Autowired
    RoleRepository roles;

    @Autowired
    AgentAvailabilityRepository availability;

    private UUID nextMember(Organization org) {
        return transactions.execute(status -> availability.lockNextAvailable(org.getId()).stream()
                .map(row -> row.getMembership().getId()).findFirst().orElse(null));
    }

    @Test
    void neverAssignedComesFirstThenTheOldestAssignment() {
        Organization org = newOrganization();
        newAvailable(org, true, Instant.parse("2026-01-01T00:00:00Z"));
        Membership older = newAvailable(org, true, Instant.parse("2025-01-01T00:00:00Z"));
        Membership never = newAvailable(org, true, null);

        assertEquals(never.getId(), nextMember(org));

        transactions.executeWithoutResult(status -> availability
                .findByMembershipIdAndOrganizationId(never.getId(), org.getId()).orElseThrow()
                .recordAssignmentAt(Instant.parse("2027-01-01T00:00:00Z")));

        assertEquals(older.getId(), nextMember(org));
    }

    @Test
    void skipsNonAssignableAndDisabledMembers() {
        Organization org = newOrganization();
        persist(new AgentAvailability(newMember(org, true)));
        newAvailable(org, false, null);

        assertEquals(null, nextMember(org));
    }

    @Test
    void anAssignableMemberIsChosenWhileAway() {
        Organization org = newOrganization();
        Membership member = newMember(org, true);
        AgentAvailability row = new AgentAvailability(member);
        row.changeAssignable(true);
        persist(row);

        assertEquals(member.getId(), nextMember(org));
    }

    @Test
    void anotherOrganizationsAgentIsNeverChosen() {
        Organization mine = newOrganization();
        newAvailable(newOrganization(), true, null);

        assertTrue(transactions.execute(status -> availability.lockNextAvailable(mine.getId())).isEmpty());
    }

    private Membership newAvailable(Organization org, boolean enabled, Instant lastAssigned) {
        Membership member = newMember(org, enabled);
        AgentAvailability row = new AgentAvailability(member);
        row.changeAssignable(true);
        if (lastAssigned != null) {
            row.recordAssignmentAt(lastAssigned);
        }
        persist(row);
        return member;
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

    private Membership newMember(Organization org, boolean enabled) {
        return transactions.execute(status -> {
            User user = new User("u" + UUID.randomUUID() + "@example.com", "hash", "Agent");
            if (!enabled) {
                user.disable();
            }
            entities.persist(user);
            Membership member = new Membership(org, user, roles.findByName("AGENT").orElseThrow());
            entities.persist(member);
            return member;
        });
    }
}
