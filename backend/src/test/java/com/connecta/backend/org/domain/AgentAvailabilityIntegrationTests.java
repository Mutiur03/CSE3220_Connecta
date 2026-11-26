package com.connecta.backend.org.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.connecta.backend.org.repository.AgentAvailabilityRepository;
import com.connecta.backend.support.RandomJwtSecret;
import com.connecta.backend.support.TestClock;
import com.connecta.backend.user.domain.User;
import com.connecta.backend.user.repository.RoleRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.time.temporal.ChronoUnit;
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

/** Real PostgreSQL: agent_availability matches the entity and its constraints hold. Skipped without Docker. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false"})
@Import(TestClock.class)
@ContextConfiguration(initializers = RandomJwtSecret.class)
class AgentAvailabilityIntegrationTests {

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
    AgentAvailabilityRepository availability;

    @Test
    void storesTheRoundRobinCursorOfAMember() {
        Membership member = newMember(newOrganization());
        AgentAvailability row = new AgentAvailability(member);
        row.recordAssignmentAt(TestClock.NOW);
        persist(row);

        AgentAvailability loaded = availability
                .findByMembershipIdAndOrganizationId(member.getId(), member.getOrganization().getId()).orElseThrow();

        assertEquals(TestClock.NOW, loaded.getLastAssignedAt().truncatedTo(ChronoUnit.MILLIS));
    }

    @Test
    void assignableDefaultsToFalseAndIsStoredWhenSwitched() {
        Membership member = newMember(newOrganization());
        AgentAvailability row = persist(new AgentAvailability(member));
        assertEquals(false, row.isAssignable());

        transactions.executeWithoutResult(status -> availability
                .findByMembershipIdAndOrganizationId(member.getId(), member.getOrganization().getId())
                .orElseThrow().changeAssignable(true));

        assertTrue(availability.findByMembershipIdAndOrganizationId(member.getId(), member.getOrganization().getId())
                .orElseThrow().isAssignable());
    }

    @Test
    void anotherOrganizationCannotReadTheRow() {
        Membership member = newMember(newOrganization());
        persist(new AgentAvailability(member));

        assertTrue(availability.findByMembershipIdAndOrganizationId(member.getId(), newOrganization().getId())
                .isEmpty());
    }

    @Test
    void rejectsASecondRowForTheSameMember() {
        Membership member = newMember(newOrganization());
        persist(new AgentAvailability(member));

        assertThrows(PersistenceException.class,
                () -> persist(new AgentAvailability(member)));
    }

    @Test
    void isRemovedWithTheMembership() {
        Membership member = newMember(newOrganization());
        AgentAvailability row = persist(new AgentAvailability(member));

        transactions.executeWithoutResult(status -> entities.remove(entities.find(Membership.class, member.getId())));

        assertTrue(availability.findById(row.getId()).isEmpty());
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
        return transactions.execute(status -> {
            User user = new User("u" + UUID.randomUUID() + "@example.com", "hash", "Agent");
            entities.persist(user);
            Membership member = new Membership(org, user, roles.findByName("AGENT").orElseThrow());
            entities.persist(member);
            return member;
        });
    }
}
