package com.connecta.backend.product.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.support.TestUsers;
import java.math.BigDecimal;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ProductTests {

    private static final Organization ORG = new Organization("Org", "org");

    @Nested
    class Construction {

        @Test
        void trimsTheNameAndAllowsNoCategory() {
            Product product = new Product(ORG, null, "  Running shoe ", "Light");

            assertEquals("Running shoe", product.getName());
            assertEquals("Light", product.getDescription());
            assertNull(product.getCategory());
        }

        @Test
        void keepsTheCategoryItWasCreatedWith() {
            ProductCategory shoes = new ProductCategory(ORG, "Shoes", null);

            Product product = new Product(ORG, shoes, "Running shoe", null);

            assertEquals(shoes, product.getCategory());
        }
    }

    @Nested
    class ChangeCategory {

        @Test
        void movesToAnotherCategoryAndCanBeUncategorized() {
            ProductCategory shoes = new ProductCategory(ORG, "Shoes", null);
            ProductCategory bags = new ProductCategory(ORG, "Bags", null);
            Product product = new Product(ORG, shoes, "Running shoe", null);

            product.changeCategory(bags);
            assertEquals(bags, product.getCategory());

            product.changeCategory(null);
            assertNull(product.getCategory());
        }
    }

    @Nested
    class Update {

        @Test
        void replacesNameAndDescriptionAndTrimsTheName() {
            Product product = new Product(ORG, null, "Shoe", null);

            product.update("  Trail shoe ", "Waterproof", null, BigDecimal.ZERO, 0);

            assertEquals("Trail shoe", product.getName());
            assertEquals("Waterproof", product.getDescription());
        }

        @Test
        void replacesSkuPriceAndReorderLevelAndTrimsTheSku() {
            Product product = new Product(ORG, null, "Shoe", null, "OLD-1", new BigDecimal("5.00"), 2);

            product.update("Shoe", null, "  NEW-2 ", new BigDecimal("19.99"), 7);

            assertEquals("NEW-2", product.getSku());
            assertEquals(new BigDecimal("19.99"), product.getUnitPrice());
            assertEquals(7, product.getReorderLevel());
        }

        @Test
        void aBlankSkuClearsIt() {
            Product product = new Product(ORG, null, "Shoe", null, "OLD-1", BigDecimal.ONE, 0);

            product.update("Shoe", null, "   ", BigDecimal.ONE, 0);

            assertNull(product.getSku());
        }
    }

    @Nested
    class InventoryFields {

        @Test
        void theShortConstructorStartsWithNoSkuZeroPriceAndNoStock() {
            Product product = new Product(ORG, null, "Shoe", null);

            assertNull(product.getSku());
            assertEquals(BigDecimal.ZERO, product.getUnitPrice());
            assertEquals(0, product.getStockQuantity());
            assertEquals(0, product.getReorderLevel());
            assertEquals(StockStatus.OUT_OF_STOCK, product.stockStatus());
        }

        @Test
        void aBlankSkuIsStoredAsNullAndAnotherIsTrimmed() {
            assertNull(new Product(ORG, null, "A", null, "  ", BigDecimal.ZERO, 0).getSku());
            assertEquals("SKU-1", new Product(ORG, null, "B", null, " SKU-1 ", BigDecimal.ZERO, 0).getSku());
        }

        @Test
        void stockStatusUsesTheStockAndTheReorderLevel() {
            Product product = new Product(ORG, null, "Shoe", null, null, BigDecimal.ZERO, 5);

            TestUsers.set(product, "stockQuantity", 5);
            assertEquals(StockStatus.LOW_STOCK, product.stockStatus());

            TestUsers.set(product, "stockQuantity", 6);
            assertEquals(StockStatus.IN_STOCK, product.stockStatus());
        }
    }

    @Nested
    class ApplyMovement {

        private Product withStock(int stock) {
            Product product = new Product(ORG, null, "Shoe", null);
            TestUsers.set(product, "stockQuantity", stock);
            return product;
        }

        @Test
        void receiveAddsTheQuantityAndReturnsThePositiveChange() {
            Product product = withStock(4);

            int change = product.applyMovement(StockMovementType.RECEIVE, 6);

            assertEquals(10, product.getStockQuantity());
            assertEquals(6, change);
        }

        @Test
        void issueTakesTheQuantityAndReturnsTheNegativeChange() {
            Product product = withStock(10);

            int change = product.applyMovement(StockMovementType.ISSUE, 3);

            assertEquals(7, product.getStockQuantity());
            assertEquals(-3, change);
        }

        @Test
        void issueCanEmptyTheStockExactly() {
            Product product = withStock(5);

            product.applyMovement(StockMovementType.ISSUE, 5);

            assertEquals(0, product.getStockQuantity());
            assertEquals(StockStatus.OUT_OF_STOCK, product.stockStatus());
        }

        @Test
        void issueBeyondTheStockIsRefusedAndLeavesStockUnchanged() {
            Product product = withStock(2);

            assertThrows(InsufficientStockException.class,
                    () -> product.applyMovement(StockMovementType.ISSUE, 3));
            assertEquals(2, product.getStockQuantity());
        }

        @Test
        void adjustSetsTheAbsoluteLevelAndReturnsTheDifference() {
            Product product = withStock(8);

            int change = product.applyMovement(StockMovementType.ADJUST, 3);

            assertEquals(3, product.getStockQuantity());
            assertEquals(-5, change);
        }

        @Test
        void adjustToZeroIsAllowedAndAdjustToTheSameLevelChangesNothing() {
            Product product = withStock(4);

            assertEquals(0, product.applyMovement(StockMovementType.ADJUST, 4));
            assertEquals(-4, product.applyMovement(StockMovementType.ADJUST, 0));
            assertEquals(0, product.getStockQuantity());
        }

        @Test
        void receiveAndIssueNeedAtLeastOneUnit() {
            Product product = withStock(4);

            assertThrows(InvalidStockQuantityException.class,
                    () -> product.applyMovement(StockMovementType.RECEIVE, 0));
            assertThrows(InvalidStockQuantityException.class,
                    () -> product.applyMovement(StockMovementType.ISSUE, 0));
            assertEquals(4, product.getStockQuantity());
        }

        @Test
        void aNegativeQuantityIsRefusedForEveryType() {
            Product product = withStock(4);

            for (StockMovementType type : StockMovementType.values()) {
                assertThrows(InvalidStockQuantityException.class, () -> product.applyMovement(type, -1));
            }
            assertEquals(4, product.getStockQuantity());
        }

        @Test
        void receiveThatWouldPassTheIntegerLimitIsRefused() {
            Product product = withStock(Integer.MAX_VALUE - 1);

            assertThrows(InvalidStockQuantityException.class,
                    () -> product.applyMovement(StockMovementType.RECEIVE, 2));
            assertEquals(Integer.MAX_VALUE - 1, product.getStockQuantity());
        }
    }
}
