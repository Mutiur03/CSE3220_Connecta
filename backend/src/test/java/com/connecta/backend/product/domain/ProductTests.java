package com.connecta.backend.product.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.connecta.backend.org.domain.Organization;
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

            product.update("  Trail shoe ", "Waterproof");

            assertEquals("Trail shoe", product.getName());
            assertEquals("Waterproof", product.getDescription());
        }
    }
}
