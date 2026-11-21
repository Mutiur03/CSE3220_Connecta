package com.connecta.backend.product.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ProductImageKeysTests {

    private static final UUID ORGANIZATION = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PRODUCT = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Nested
    class Create {

        @Test
        void theKeyCarriesTheOrganizationAndTheProduct() {
            String key = ProductImageKeys.create(ORGANIZATION, PRODUCT, "image/png");

            assertTrue(key.startsWith("organizations/" + ORGANIZATION + "/products/" + PRODUCT + "/"));
        }

        @Test
        void theNameIsGeneratedAndTheExtensionFollowsTheContentType() {
            String first = ProductImageKeys.create(ORGANIZATION, PRODUCT, "image/jpeg");
            String second = ProductImageKeys.create(ORGANIZATION, PRODUCT, "image/jpeg");

            assertTrue(first.endsWith(".jpg"));
            assertNotEquals(first, second);
        }

        @Test
        void eachAllowedTypeMapsToItsExtension() {
            assertTrue(ProductImageKeys.create(ORGANIZATION, PRODUCT, "image/webp").endsWith(".webp"));
            assertTrue(ProductImageKeys.create(ORGANIZATION, PRODUCT, "image/gif").endsWith(".gif"));
        }

        @Test
        void anUnsupportedOrMissingTypeIsRejected() {
            assertThrows(UnsupportedProductImageTypeException.class,
                    () -> ProductImageKeys.create(ORGANIZATION, PRODUCT, "application/pdf"));
            assertThrows(UnsupportedProductImageTypeException.class,
                    () -> ProductImageKeys.create(ORGANIZATION, PRODUCT, null));
        }
    }

    @Nested
    class OrganizationPrefix {

        @Test
        void matchesTheStartOfEveryKeyOfThatOrganization() {
            assertEquals("organizations/" + ORGANIZATION + "/", ProductImageKeys.organizationPrefix(ORGANIZATION));
        }
    }
}
