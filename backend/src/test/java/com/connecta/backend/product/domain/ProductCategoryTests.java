package com.connecta.backend.product.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.connecta.backend.org.domain.Organization;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ProductCategoryTests {

    private static final Organization ORG = new Organization("Org", "org");

    private static ProductCategory withId(ProductCategory category, UUID id) {
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }

    @Nested
    class Construction {

        @Test
        void trimsTheNameAndKeepsTheDescription() {
            ProductCategory category = new ProductCategory(ORG, "  Shoes  ", "Footwear");

            assertEquals("Shoes", category.getName());
            assertEquals("Footwear", category.getDescription());
            assertEquals(ORG, category.getOrganization());
        }
    }

    @Nested
    class Update {

        @Test
        void replacesNameAndDescription() {
            ProductCategory category = new ProductCategory(ORG, "Shoes", null);

            category.update(" Footwear ", "All footwear");

            assertEquals("Footwear", category.getName());
            assertEquals("All footwear", category.getDescription());
        }
    }

    @Nested
    class Identity {

        @Test
        void twoCategoriesWithTheSameIdAreEqual() {
            UUID id = UUID.randomUUID();
            ProductCategory first = withId(new ProductCategory(ORG, "A", null), id);
            ProductCategory second = withId(new ProductCategory(ORG, "B", null), id);

            assertEquals(first, second);
            assertEquals(first.hashCode(), second.hashCode());
        }

        @Test
        void unsavedCategoriesAreEqualOnlyToThemselves() {
            ProductCategory first = new ProductCategory(ORG, "A", null);
            ProductCategory second = new ProductCategory(ORG, "A", null);

            assertTrue(first.equals(first));
            assertFalse(first.equals(second));
        }

        @Test
        void categoriesWithDifferentIdsAreNotEqual() {
            ProductCategory first = withId(new ProductCategory(ORG, "A", null), UUID.randomUUID());
            ProductCategory second = withId(new ProductCategory(ORG, "A", null), UUID.randomUUID());

            assertNotEquals(first, second);
        }
    }
}
