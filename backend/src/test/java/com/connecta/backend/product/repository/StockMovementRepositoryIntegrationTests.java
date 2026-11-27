package com.connecta.backend.product.repository;

import com.connecta.backend.support.SharedPostgres;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.org.repository.OrganizationRepository;
import com.connecta.backend.product.domain.Product;
import com.connecta.backend.product.domain.StockMovement;
import com.connecta.backend.product.domain.StockMovementType;
import com.connecta.backend.support.RandomJwtSecret;
import com.connecta.backend.support.TestClock;
import com.connecta.backend.user.domain.User;
import com.connecta.backend.user.repository.UserRepository;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Real PostgreSQL: the stock_movements table, the organization filter of the history queries, the newest-first
 * order, and the row lock taken by the product lookup. Skipped when Docker is absent.
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false"})
@Import(TestClock.class)
@ContextConfiguration(initializers = RandomJwtSecret.class)
class StockMovementRepositoryIntegrationTests extends SharedPostgres {

    /** SQLSTATE raised when NOWAIT finds the row locked by another transaction. */
    private static final String LOCK_NOT_AVAILABLE = "55P03";

    @Autowired
    OrganizationRepository organizations;

    @Autowired
    UserRepository users;

    @Autowired
    ProductRepository products;

    @Autowired
    StockMovementRepository movements;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PlatformTransactionManager transactions;

    private Organization newOrganization() {
        return organizations.saveAndFlush(new Organization("Org", "org-" + System.nanoTime()));
    }

    private Product newProduct(Organization org, String name) {
        return products.saveAndFlush(new Product(org, null, name, null, null, BigDecimal.TEN, 0));
    }

    private User newUser() {
        return users.saveAndFlush(new User("u" + UUID.randomUUID() + "@example.com", "hash", "Stock Keeper"));
    }

    private StockMovement receive(Product product, int quantity, User by) {
        return movements.saveAndFlush(new StockMovement(product, StockMovementType.RECEIVE, quantity, quantity,
                "Delivery", by));
    }

    private void createdAt(StockMovement movement, Instant when) {
        jdbc.update("UPDATE stock_movements SET created_at = ? WHERE id = ?",
                Timestamp.from(when), movement.getId());
    }

    @Nested
    class History {

        @Test
        void anOrganizationSeesOnlyItsOwnMovements() {
            Organization acme = newOrganization();
            Organization globex = newOrganization();
            User keeper = newUser();
            receive(newProduct(acme, "Hats"), 3, keeper);
            receive(newProduct(globex, "Secret"), 9, keeper);

            var page = movements.findByOrganizationId(acme.getId(), PageRequest.of(0, 20));

            assertEquals(1, page.getTotalElements());
            assertEquals(acme.getId(), page.getContent().get(0).getOrganization().getId());
        }

        @Test
        void aProductFilterNeverCrossesOrganizations() {
            Organization acme = newOrganization();
            Organization globex = newOrganization();
            User keeper = newUser();
            Product globexProduct = newProduct(globex, "Secret");
            receive(globexProduct, 9, keeper);

            var page = movements.findByOrganizationIdAndProductId(acme.getId(), globexProduct.getId(),
                    PageRequest.of(0, 20));

            assertEquals(0, page.getTotalElements());
        }

        @Test
        void aProductsMovementsComeBackNewestFirst() {
            Organization acme = newOrganization();
            User keeper = newUser();
            Product hats = newProduct(acme, "Hats");
            StockMovement older = receive(hats, 1, keeper);
            StockMovement newer = receive(hats, 2, keeper);
            createdAt(older, Instant.parse("2026-01-01T10:00:00Z"));
            createdAt(newer, Instant.parse("2026-01-02T10:00:00Z"));

            var page = movements.findByOrganizationIdAndProductId(acme.getId(), hats.getId(),
                    PageRequest.of(0, 20, Sort.by("createdAt").descending()));

            assertEquals(newer.getId(), page.getContent().get(0).getId());
            assertEquals(older.getId(), page.getContent().get(1).getId());
        }

        @Test
        void aMovementOutlivesTheUserWhoMadeIt() {
            Organization acme = newOrganization();
            User keeper = newUser();
            StockMovement movement = receive(newProduct(acme, "Hats"), 1, keeper);

            jdbc.update("DELETE FROM users WHERE id = ?", keeper.getId());

            assertNull(movements.findById(movement.getId()).orElseThrow().getCreatedBy());
        }
    }

    @Nested
    class LockedProductLookup {

        @Test
        void returnsTheProductOnlyForItsOwnOrganization() {
            Organization acme = newOrganization();
            Organization globex = newOrganization();
            Product hats = newProduct(acme, "Hats");
            TransactionTemplate inTransaction = new TransactionTemplate(transactions);

            inTransaction.executeWithoutResult(status -> {
                assertTrue(products.findForUpdateByIdAndOrganizationId(hats.getId(), acme.getId()).isPresent());
                assertFalse(products.findForUpdateByIdAndOrganizationId(hats.getId(), globex.getId()).isPresent());
            });
        }

        @Test
        void theRowStaysLockedUntilTheTransactionEnds() throws SQLException {
            Organization acme = newOrganization();
            Product hats = newProduct(acme, "Hats");
            TransactionTemplate inTransaction = new TransactionTemplate(transactions);

            inTransaction.executeWithoutResult(status -> {
                products.findForUpdateByIdAndOrganizationId(hats.getId(), acme.getId());

                SQLException refused = assertThrows(SQLException.class, () -> selectForUpdateNoWait(hats.getId()));
                assertEquals(LOCK_NOT_AVAILABLE, refused.getSQLState());
            });

            selectForUpdateNoWait(hats.getId()).close();
        }

        /** A second connection asks for the same row without waiting; it succeeds only when nothing holds it. */
        private Connection selectForUpdateNoWait(UUID productId) throws SQLException {
            Connection other = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                    POSTGRES.getPassword());
            other.setAutoCommit(false);
            try (var statement = other.prepareStatement("SELECT id FROM products WHERE id = ? FOR UPDATE NOWAIT")) {
                statement.setObject(1, productId);
                statement.executeQuery();
                other.rollback();
            } catch (SQLException e) {
                other.close();
                throw e;
            }
            return other;
        }
    }
}
