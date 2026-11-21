package com.connecta.backend.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.connecta.backend.product.service.LocalProductImageStorage;
import com.connecta.backend.product.service.ProductImageStorage;
import com.connecta.backend.product.service.R2ProductImageStorage;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ProductImageStorageConfigTests {

    private static final String BASE_URL = "https://images.example.test";

    private static R2Properties r2(boolean required, String bucket, String publicBaseUrl) {
        return new R2Properties("test-account", bucket, "test-access-key", "test-secret-key", publicBaseUrl,
                required);
    }

    @Nested
    class Selection {

        @Test
        void devFallsBackToTheLocalStoreWhenR2IsNotConfigured() {
            R2Properties unset = new R2Properties("", "", "", "", "", false);

            assertInstanceOf(LocalProductImageStorage.class,
                    new ProductImageStorageConfig().productImageStorage(unset));
        }

        @Test
        void aPartialR2ConfigurationIsNotConfiguredInDev() {
            assertInstanceOf(LocalProductImageStorage.class,
                    new ProductImageStorageConfig().productImageStorage(r2(false, " ", BASE_URL)));
        }

        @Test
        void aCompleteR2ConfigurationSelectsTheR2Adapter() {
            assertInstanceOf(R2ProductImageStorage.class,
                    new ProductImageStorageConfig().productImageStorage(r2(false, "bucket", BASE_URL)));
        }

        @Test
        void prodFailsFastWhenR2IsRequiredButMissing() {
            R2Properties unset = new R2Properties(null, null, null, null, null, true);

            IllegalStateException thrown = assertThrows(IllegalStateException.class,
                    () -> new ProductImageStorageConfig().productImageStorage(unset));

            assertTrue(thrown.getMessage().contains("R2_BUCKET"));
            assertTrue(!thrown.getMessage().contains("test-secret-key"));
        }

        @Test
        void prodFailsFastWhenOneR2SettingIsMissing() {
            assertThrows(IllegalStateException.class,
                    () -> new ProductImageStorageConfig().productImageStorage(r2(true, "", BASE_URL)));
        }

        @Test
        void prodAcceptsACompleteR2Configuration() {
            assertInstanceOf(R2ProductImageStorage.class,
                    new ProductImageStorageConfig().productImageStorage(r2(true, "bucket", BASE_URL)));
        }
    }

    @Nested
    class PublicBaseUrl {

        @Test
        void aTrailingSlashIsNotDoubled() {
            ProductImageStorage storage = new ProductImageStorageConfig()
                    .productImageStorage(r2(false, "bucket", BASE_URL + "/"));

            assertEquals(BASE_URL + "/organizations/x/z.png", storage.publicUrl("organizations/x/z.png"));
        }
    }
}
