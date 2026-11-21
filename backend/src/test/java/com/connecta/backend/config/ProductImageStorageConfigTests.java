package com.connecta.backend.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.connecta.backend.product.service.LocalProductImageStorage;
import com.connecta.backend.product.service.ProductImageStorage;
import com.connecta.backend.product.service.S3ProductImageStorage;
import java.net.URI;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;

class ProductImageStorageConfigTests {

    private static final String BASE_URL = "https://images.example.test";
    private static final String SECRET = "test-secret-key";
    private static final String ENDPOINT = "http://minio.example.test:9000";

    private static S3StorageProperties s3(String endpoint, String bucket, String publicBaseUrl, boolean required) {
        return new S3StorageProperties(endpoint, "us-east-1", bucket, "test-access-key", SECRET, false,
                publicBaseUrl, required);
    }

    @Nested
    class Selection {

        @Test
        void devFallsBackToTheLocalStoreWhenS3IsNotConfigured() {
            S3StorageProperties unset = new S3StorageProperties("", "", "", "", "", false, "", false);

            assertInstanceOf(LocalProductImageStorage.class,
                    new ProductImageStorageConfig().productImageStorage(unset));
        }

        @Test
        void aPartialS3ConfigurationIsNotConfiguredInDev() {
            assertInstanceOf(LocalProductImageStorage.class,
                    new ProductImageStorageConfig().productImageStorage(s3("", " ", BASE_URL, false)));
        }

        @Test
        void aCompleteS3ConfigurationSelectsTheS3Adapter() {
            assertInstanceOf(S3ProductImageStorage.class,
                    new ProductImageStorageConfig().productImageStorage(s3("", "bucket", BASE_URL, false)));
        }

        @Test
        void anEndpointIsOptionalWhenTheRestIsConfigured() {
            assertInstanceOf(S3ProductImageStorage.class,
                    new ProductImageStorageConfig().productImageStorage(s3("", "bucket", BASE_URL, true)));
        }

        @Test
        void prodFailsFastWhenS3IsRequiredButMissing() {
            S3StorageProperties unset = new S3StorageProperties(null, null, null, null, null, false, null, true);

            IllegalStateException thrown = assertThrows(IllegalStateException.class,
                    () -> new ProductImageStorageConfig().productImageStorage(unset));

            assertTrue(thrown.getMessage().contains("S3_BUCKET"));
        }

        @Test
        void prodFailsFastWhenOneS3SettingIsMissing() {
            assertThrows(IllegalStateException.class,
                    () -> new ProductImageStorageConfig().productImageStorage(s3("", "", BASE_URL, true)));
        }

        @Test
        void prodFailsFastWhenThePublicBaseUrlIsMissing() {
            IllegalStateException thrown = assertThrows(IllegalStateException.class,
                    () -> new ProductImageStorageConfig().productImageStorage(s3("", "bucket", "", true)));

            assertTrue(thrown.getMessage().contains("S3_PUBLIC_BASE_URL"));
            assertFalse(thrown.getMessage().contains(SECRET));
        }

        @Test
        void prodAcceptsACompleteS3Configuration() {
            assertInstanceOf(S3ProductImageStorage.class,
                    new ProductImageStorageConfig().productImageStorage(s3("", "bucket", BASE_URL, true)));
        }
    }

    @Nested
    class ClientSettings {

        @Test
        void theEndpointIsOverriddenOnlyWhenConfigured() {
            S3ClientBuilder builder = mock(S3ClientBuilder.class, RETURNS_SELF);

            ProductImageStorageConfig.configure(builder, s3("", "bucket", BASE_URL, false));

            verify(builder, never()).endpointOverride(any(URI.class));
        }

        @Test
        void theConfiguredEndpointIsApplied() {
            S3ClientBuilder builder = mock(S3ClientBuilder.class, RETURNS_SELF);

            ProductImageStorageConfig.configure(builder, s3(ENDPOINT, "bucket", BASE_URL, false));

            verify(builder).endpointOverride(URI.create(ENDPOINT));
        }

        @Test
        void pathStyleAccessIsAppliedFromConfiguration() {
            S3ClientBuilder builder = mock(S3ClientBuilder.class, RETURNS_SELF);
            S3StorageProperties pathStyle = new S3StorageProperties("", "", "bucket", "key", SECRET, true,
                    BASE_URL, false);

            ProductImageStorageConfig.configure(builder, pathStyle);

            ArgumentCaptor<S3Configuration> serviceConfig = ArgumentCaptor.forClass(S3Configuration.class);
            verify(builder).serviceConfiguration(serviceConfig.capture());
            assertEquals(Boolean.TRUE, serviceConfig.getValue().pathStyleAccessEnabled());
        }

        @Test
        void pathStyleAccessIsOffByDefault() {
            S3ClientBuilder builder = mock(S3ClientBuilder.class, RETURNS_SELF);

            ProductImageStorageConfig.configure(builder, s3("", "bucket", BASE_URL, false));

            ArgumentCaptor<S3Configuration> serviceConfig = ArgumentCaptor.forClass(S3Configuration.class);
            verify(builder).serviceConfiguration(serviceConfig.capture());
            assertEquals(Boolean.FALSE, serviceConfig.getValue().pathStyleAccessEnabled());
        }

        @Test
        void aBlankRegionFallsBackToUsEast1() {
            S3StorageProperties noRegion = new S3StorageProperties("", "", "bucket", "key", SECRET, false,
                    BASE_URL, false);

            assertEquals("us-east-1", noRegion.region());
        }
    }

    @Nested
    class PublicBaseUrl {

        @Test
        void aTrailingSlashIsNotDoubled() {
            ProductImageStorage storage = new ProductImageStorageConfig()
                    .productImageStorage(s3("", "bucket", BASE_URL + "/", false));

            assertEquals(BASE_URL + "/organizations/x/z.png", storage.publicUrl("organizations/x/z.png"));
        }
    }
}
