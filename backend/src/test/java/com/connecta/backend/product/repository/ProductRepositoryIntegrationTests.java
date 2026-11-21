package com.connecta.backend.product.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.org.repository.OrganizationRepository;
import com.connecta.backend.product.domain.Product;
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
 * Real PostgreSQL: the product table, its optional category link and the same-organization
 * foreign key are enforced by the database. Skipped when Docker is absent.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false"})
@Import(TestClock.class)
@ContextConfiguration(initializers = RandomJwtSecret.class)
class ProductRepositoryIntegrationTests {

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

    @Autowired
    ProductRepository products;

    private Organization newOrganization() {
        return organizations.saveAndFlush(new Organization("Org", "org-" + System.nanoTime()));
    }

    @Nested
    class CategoryLink {

        @Test
        void aProductCanBeUncategorized() {
            Organization org = newOrganization();
            Product product = products.saveAndFlush(new Product(org, null, "Mystery item", null));

            Product found = products.findByIdAndOrganizationId(product.getId(), org.getId()).orElseThrow();

            assertNull(found.getCategory());
        }

        @Test
        void listsOnlyTheProductsInTheRequestedCategory() {
            Organization org = newOrganization();
            ProductCategory shoes = categories.saveAndFlush(new ProductCategory(org, "Shoes", null));
            ProductCategory bags = categories.saveAndFlush(new ProductCategory(org, "Bags", null));
            products.saveAndFlush(new Product(org, shoes, "Running shoe", null));
            products.saveAndFlush(new Product(org, bags, "Tote", null));

            var page = products.findByOrganizationIdAndCategoryId(org.getId(), shoes.getId(),
                    PageRequest.of(0, 20, Sort.by("name")));

            assertEquals(1, page.getTotalElements());
            assertEquals("Running shoe", page.getContent().get(0).getName());
        }

        @Test
        void aProductCannotPointAtAnotherOrganizationsCategory() {
            Organization mine = newOrganization();
            Organization other = newOrganization();
            ProductCategory foreignShoes = categories.saveAndFlush(new ProductCategory(other, "Shoes", null));

            assertThrows(DataIntegrityViolationException.class,
                    () -> products.saveAndFlush(new Product(mine, foreignShoes, "Running shoe", null)));
        }
    }

    @Nested
    class TenantScope {

        @Test
        void lookupByIdReturnsNothingForAnotherOrganization() {
            Organization mine = newOrganization();
            Organization other = newOrganization();
            Product product = products.saveAndFlush(new Product(mine, null, "Running shoe", null));

            assertTrue(products.findByIdAndOrganizationId(product.getId(), mine.getId()).isPresent());
            assertFalse(products.findByIdAndOrganizationId(product.getId(), other.getId()).isPresent());
        }

        @Test
        void listOnlyReturnsTheOrganizationsOwnProducts() {
            Organization mine = newOrganization();
            Organization other = newOrganization();
            products.saveAndFlush(new Product(mine, null, "Running shoe", null));
            products.saveAndFlush(new Product(other, null, "Tote", null));

            var page = products.findByOrganizationId(mine.getId(), PageRequest.of(0, 20, Sort.by("name")));

            assertEquals(1, page.getTotalElements());
            assertEquals("Running shoe", page.getContent().get(0).getName());
        }

        @Test
        void categoryFilterDoesNotLeakAnotherOrganizationsProducts() {
            Organization mine = newOrganization();
            Organization other = newOrganization();
            ProductCategory otherShoes = categories.saveAndFlush(new ProductCategory(other, "Shoes", null));
            products.saveAndFlush(new Product(other, otherShoes, "Their shoe", null));

            var page = products.findByOrganizationIdAndCategoryId(mine.getId(), otherShoes.getId(),
                    PageRequest.of(0, 20, Sort.by("name")));

            assertEquals(0, page.getTotalElements());
        }
    }

    @Nested
    class Constraints {

        @Test
        void nameIsUniqueWithinOneOrganizationOnly() {
            Organization mine = newOrganization();
            Organization other = newOrganization();
            products.saveAndFlush(new Product(mine, null, "Running shoe", null));

            assertThrows(DataIntegrityViolationException.class,
                    () -> products.saveAndFlush(new Product(mine, null, "Running shoe", null)));
            products.saveAndFlush(new Product(other, null, "Running shoe", null));
        }

        @Test
        void existsByNameIsScopedToTheOrganization() {
            Organization mine = newOrganization();
            Organization other = newOrganization();
            products.saveAndFlush(new Product(mine, null, "Running shoe", null));

            assertTrue(products.existsByOrganizationIdAndName(mine.getId(), "Running shoe"));
            assertFalse(products.existsByOrganizationIdAndName(other.getId(), "Running shoe"));
        }
    }
}
