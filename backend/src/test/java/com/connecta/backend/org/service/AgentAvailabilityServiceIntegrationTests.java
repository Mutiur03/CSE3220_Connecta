package com.connecta.backend.org.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.connecta.backend.org.domain.Membership;
import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.org.dto.AssignableResponse;
import com.connecta.backend.support.RandomJwtSecret;
import com.connecta.backend.support.TestClock;
import com.connecta.backend.user.domain.User;
import com.connecta.backend.user.repository.RoleRepository;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Real PostgreSQL: two first toggles of one member at once. Skipped without Docker. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false"})
@Import(TestClock.class)
@ContextConfiguration(initializers = RandomJwtSecret.class)
class AgentAvailabilityServiceIntegrationTests {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    AgentAvailabilityService service;

    @Autowired
    EntityManager entities;

    @Autowired
    TransactionTemplate transactions;

    @Autowired
    RoleRepository roles;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void twoConcurrentFirstTogglesBothSucceedWithOneRow() throws Exception {
        Organization org = transactions.execute(s -> {
            Organization created = new Organization("Org " + UUID.randomUUID(), "org-" + UUID.randomUUID());
            entities.persist(created);
            return created;
        });
        User admin = newUser();
        newMember(org, admin, "ADMIN");
        Membership target = newMember(org, newUser(), "AGENT");

        int callers = 2;
        CountDownLatch ready = new CountDownLatch(callers);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        try {
            List<Future<AssignableResponse>> results = new ArrayList<>();
            for (int i = 0; i < callers; i++) {
                Callable<AssignableResponse> toggle = () -> {
                    ready.countDown();
                    go.await();
                    return service.setAssignable(admin.getId().toString(), org.getId(), target.getId(), true);
                };
                results.add(pool.submit(toggle));
            }
            ready.await();
            go.countDown();
            for (Future<AssignableResponse> result : results) {
                assertTrue(result.get().assignable());
            }
        } finally {
            pool.shutdownNow();
        }

        assertEquals(1, jdbc.queryForObject(
                "select count(*) from agent_availability where membership_id = ?", Integer.class, target.getId()));
    }

    private User newUser() {
        return transactions.execute(s -> {
            User user = new User("u" + UUID.randomUUID() + "@example.com", "hash", "Agent");
            entities.persist(user);
            return user;
        });
    }

    private Membership newMember(Organization org, User user, String role) {
        return transactions.execute(s -> {
            Membership member = new Membership(org, user, roles.findByName(role).orElseThrow());
            entities.persist(member);
            return member;
        });
    }
}
