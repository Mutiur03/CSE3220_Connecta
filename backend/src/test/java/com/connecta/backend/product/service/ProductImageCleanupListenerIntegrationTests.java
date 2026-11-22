package com.connecta.backend.product.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.connecta.backend.org.domain.Membership;
import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.org.repository.MembershipRepository;
import com.connecta.backend.org.repository.OrganizationRepository;
import com.connecta.backend.product.domain.Product;
import com.connecta.backend.product.domain.ProductImage;
import com.connecta.backend.product.repository.ProductImageRepository;
import com.connecta.backend.product.repository.ProductRepository;
import com.connecta.backend.support.RandomJwtSecret;
import com.connecta.backend.support.TestClock;
import com.connecta.backend.user.domain.Roles;
import com.connecta.backend.user.domain.User;
import com.connecta.backend.user.repository.RoleRepository;
import com.connecta.backend.user.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The stored object of a deleted product is removed only once the delete has committed. Real PostgreSQL, so a
 * rollback is a real rollback.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false"})
@Import(TestClock.class)
@ContextConfiguration(initializers = RandomJwtSecret.class)
class ProductImageCleanupListenerIntegrationTests {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @MockitoBean
    ProductImageStorage storage;

    @Autowired
    ProductService productService;

    @Autowired
    PlatformTransactionManager transactions;

    @Autowired
    ProductRepository products;

    @Autowired
    ProductImageRepository images;

    @Autowired
    OrganizationRepository organizations;

    @Autowired
    MembershipRepository memberships;

    @Autowired
    UserRepository users;

    @Autowired
    RoleRepository roles;

    @Test
    void aRolledBackDeleteNeverRemovesTheStoredObject() {
        Fixture fixture = fixtureWithImage("key-rollback");

        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(transactions).executeWithoutResult(
                status -> {
                    productService.delete(fixture.subject(), fixture.organizationId(), fixture.productId());
                    throw new IllegalStateException("rolled back on purpose");
                }));

        verify(storage, never()).delete(anyString());
        assertTrue(products.existsById(fixture.productId()));
        assertTrue(images.existsById(fixture.imageId()));
    }

    @Test
    void theStoredObjectIsRemovedOnceTheDeleteCommits() {
        Fixture fixture = fixtureWithImage("key-commit");

        productService.delete(fixture.subject(), fixture.organizationId(), fixture.productId());

        verify(storage).delete("key-commit");
        assertFalse(products.existsById(fixture.productId()));
    }

    private Fixture fixtureWithImage(String objectKey) {
        Organization org = organizations.saveAndFlush(new Organization("Acme", "acme-" + UUID.randomUUID()));
        User admin = users.saveAndFlush(newUser());
        memberships.saveAndFlush(new Membership(org, admin, roles.findByName(Roles.ADMIN).orElseThrow()));
        Product shoes = products.saveAndFlush(new Product(org, null, "Shoes", "A product"));
        ProductImage image = images.saveAndFlush(new ProductImage(shoes, objectKey, "image/png", 12, 0));
        return new Fixture(admin.getId().toString(), org.getId(), shoes.getId(), image.getId());
    }

    private User newUser() {
        User user = new User("u" + UUID.randomUUID() + "@example.com", "hash", "Test User");
        user.addRole(roles.findByName(Roles.AGENT).orElseThrow());
        return user;
    }

    private record Fixture(String subject, UUID organizationId, UUID productId, UUID imageId) {
    }
}
