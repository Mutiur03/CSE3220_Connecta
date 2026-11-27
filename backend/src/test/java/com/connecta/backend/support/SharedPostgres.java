package com.connecta.backend.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base class for database tests: one PostgreSQL container for the whole test JVM instead of one per class.
 * Tests share the database, so each one creates its own data (unique emails and slugs) and never assumes an
 * empty table. A test that needs a clean schema (Flyway clean/target) or lists every row of a table keeps its own container.
 * max_connections is raised because each cached Spring context holds its own connection pool.
 */
@Testcontainers(disabledWithoutDocker = true)
public abstract class SharedPostgres {

    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine")
            .withCommand("postgres", "-c", "max_connections=500");

    static {
        if (DockerClientFactory.instance().isDockerAvailable()) {
            POSTGRES.start();
        }
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
