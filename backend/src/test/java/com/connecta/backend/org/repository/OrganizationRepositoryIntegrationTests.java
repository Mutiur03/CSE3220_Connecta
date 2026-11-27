package com.connecta.backend.org.repository;

import com.connecta.backend.support.SharedPostgres;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.support.RandomJwtSecret;
import com.connecta.backend.support.TestClock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ContextConfiguration;

/**
 * Real PostgreSQL: the organizations migration is applied by Flyway and the constraints it defines
 * (unique slug, slug format) are enforced by the database. Skipped when Docker is absent.
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false"})
@Import(TestClock.class)
@ContextConfiguration(initializers = RandomJwtSecret.class)
class OrganizationRepositoryIntegrationTests extends SharedPostgres {

    @Autowired
    OrganizationRepository organizations;

    @Test
    void savedOrganizationIsFoundBySlugAndGetsTimestamps() {
        String slug = "find-" + System.nanoTime();
        organizations.saveAndFlush(new Organization("Find Me", slug));

        Organization found = organizations.findBySlug(slug).orElseThrow();

        assertEquals("Find Me", found.getName());
        assertNotNull(found.getCreatedAt());
        assertNotNull(found.getUpdatedAt());
        assertTrue(organizations.existsBySlug(slug));
        assertFalse(organizations.existsBySlug(slug + "-missing"));
    }

    @Test
    void duplicateSlugIsRejectedByTheDatabase() {
        String slug = "dup-" + System.nanoTime();
        organizations.saveAndFlush(new Organization("First", slug));

        assertThrows(DataIntegrityViolationException.class,
                () -> organizations.saveAndFlush(new Organization("Second", slug)));
    }

    @Test
    void slugThatIsNotKebabCaseIsRejectedByTheDatabase() {
        assertThrows(DataIntegrityViolationException.class,
                () -> organizations.saveAndFlush(new Organization("Bad", "Not Kebab")));
    }
}
