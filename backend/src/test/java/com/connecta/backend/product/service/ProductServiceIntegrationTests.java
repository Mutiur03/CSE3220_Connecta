package com.connecta.backend.product.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.connecta.backend.common.dto.PageResponse;
import com.connecta.backend.org.domain.Membership;
import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.org.repository.MembershipRepository;
import com.connecta.backend.org.repository.OrganizationRepository;
import com.connecta.backend.product.domain.Product;
import com.connecta.backend.product.domain.ProductCategory;
import com.connecta.backend.product.dto.ProductResponse;
import com.connecta.backend.product.repository.ProductCategoryRepository;
import com.connecta.backend.product.repository.ProductRepository;
import com.connecta.backend.support.RandomJwtSecret;
import com.connecta.backend.support.TestClock;
import com.connecta.backend.user.domain.Roles;
import com.connecta.backend.user.domain.User;
import com.connecta.backend.user.repository.RoleRepository;
import com.connecta.backend.user.repository.UserRepository;
import jakarta.persistence.EntityManagerFactory;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The product list must not issue one extra statement per product (the category is fetched with the page). Hibernate
 * Statistics count the prepared statements of each list call on real PostgreSQL; the count must match for a small
 * and a full page of the same products.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false"})
@Import(TestClock.class)
@ContextConfiguration(initializers = RandomJwtSecret.class)
class ProductServiceIntegrationTests {

    private static final int PRODUCTS = 10;

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    ProductService service;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    @Autowired
    ProductRepository products;

    @Autowired
    ProductCategoryRepository categories;

    @Autowired
    OrganizationRepository organizations;

    @Autowired
    MembershipRepository memberships;

    @Autowired
    UserRepository users;

    @Autowired
    RoleRepository roles;

    @Test
    void theStatementCountDoesNotGrowWithThePageSize() {
        Organization org = organizations.saveAndFlush(new Organization("Acme", "acme-" + UUID.randomUUID()));
        User agent = users.saveAndFlush(newAgent());
        memberships.saveAndFlush(new Membership(org, agent, roles.findByName(Roles.AGENT).orElseThrow()));
        for (int i = 0; i < PRODUCTS; i++) {
            ProductCategory category = categories.saveAndFlush(new ProductCategory(org, "Category " + i, null));
            products.saveAndFlush(new Product(org, category, "Product " + i, "A product"));
        }
        String subject = agent.getId().toString();
        Statistics statistics = statistics();

        statementsForPage(statistics, subject, org.getId(), 0, 1);
        long smallPage = statementsForPage(statistics, subject, org.getId(), 0, 2);
        long fullPage = statementsForPage(statistics, subject, org.getId(), 0, PRODUCTS);

        assertEquals(smallPage, fullPage, "the product list issues a statement per product");
    }

    private long statementsForPage(Statistics statistics, String subject, UUID organizationId, int page, int size) {
        statistics.clear();
        PageResponse<ProductResponse> result = service.list(subject, organizationId, new ProductFilter(null, null, null),
                page, size);
        assertEquals(size, result.content().size());
        return statistics.getPrepareStatementCount();
    }

    private Statistics statistics() {
        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        sessionFactory.getStatistics().setStatisticsEnabled(true);
        return sessionFactory.getStatistics();
    }

    private User newAgent() {
        User user = new User("u" + UUID.randomUUID() + "@example.com", "hash", "Test User");
        user.addRole(roles.findByName(Roles.AGENT).orElseThrow());
        return user;
    }
}
