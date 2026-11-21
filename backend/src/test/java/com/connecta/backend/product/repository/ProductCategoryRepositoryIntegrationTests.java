package com.connecta.backend.product.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.org.repository.OrganizationRepository;
import com.connecta.backend.product.domain.ProductCategory;
import com.connecta.backend.support.RandomJwtSecret;
import com.connecta.backend.support.TestClock;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Real PostgreSQL: the product catalog migration is applied by Flyway, and the category queries
 * are checked for tenant scope. Skipped when Docker is absent.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false"})
@Import(TestClock.class)
@ContextConfiguration(initializers = RandomJwtSecret.class)
class ProductCategoryRepositoryIntegrationTests {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    OrganizationRepository organizations;

    @Autowired
    ProductCategoryRepository categories;

    private Organization newOrganization() {
        return organizations.saveAndFlush(new Organization("Org", "org-" + System.nanoTime()));
    }

    @Nested
    class TenantScope {

        @Test
        void lookupByIdReturnsNothingForAnotherOrganization() {
            Organization mine = newOrganization();
            Organization other = newOrganization();
            ProductCategory shoes = categories.saveAndFlush(new ProductCategory(mine, "Shoes", null));

            assertTrue(categories.findByIdAndOrganizationId(shoes.getId(), mine.getId()).isPresent());
            assertFalse(categories.findByIdAndOrganizationId(shoes.getId(), other.getId()).isPresent());
        }

        @Test
        void listOnlyReturnsTheOrganizationsOwnCategories() {
            Organization mine = newOrganization();
            Organization other = newOrganization();
            categories.saveAndFlush(new ProductCategory(mine, "Shoes", null));
            categories.saveAndFlush(new ProductCategory(other, "Bags", null));

            var page = categories.findByOrganizationId(mine.getId(), PageRequest.of(0, 20, Sort.by("name")));

            assertEquals(1, page.getTotalElements());
            assertEquals("Shoes", page.getContent().get(0).getName());
        }
    }

    @Nested
    class Constraints {

        @Test
        void nameIsUniqueWithinOneOrganizationOnly() {
            Organization mine = newOrganization();
            Organization other = newOrganization();
            categories.saveAndFlush(new ProductCategory(mine, "Shoes", null));

            assertThrows(DataIntegrityViolationException.class,
                    () -> categories.saveAndFlush(new ProductCategory(mine, "Shoes", null)));
            categories.saveAndFlush(new ProductCategory(other, "Shoes", null));
        }

        @Test
        void existsByNameIsScopedToTheOrganization() {
            Organization mine = newOrganization();
            Organization other = newOrganization();
            categories.saveAndFlush(new ProductCategory(mine, "Shoes", null));

            assertTrue(categories.existsByOrganizationIdAndName(mine.getId(), "Shoes"));
            assertFalse(categories.existsByOrganizationIdAndName(other.getId(), "Shoes"));
        }
    }
}
