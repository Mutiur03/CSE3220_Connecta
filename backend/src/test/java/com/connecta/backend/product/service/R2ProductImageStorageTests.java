package com.connecta.backend.product.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@ExtendWith(MockitoExtension.class)
class R2ProductImageStorageTests {

    private static final String BUCKET = "test-bucket";
    private static final String PUBLIC_BASE_URL = "https://images.example.test";
    private static final UUID ORGANIZATION = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PRODUCT = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String SECRET = "super-secret-access-key-value";
    private static final byte[] CONTENT = {1, 2, 3};

    @Mock
    private S3Client s3;

    private R2ProductImageStorage storage;

    @BeforeEach
    void setUp() {
        storage = new R2ProductImageStorage(s3, BUCKET, PUBLIC_BASE_URL);
    }

    @Nested
    class Store {

        @Test
        void putsTheBytesUnderTheGeneratedTenantScopedKey() {
            String key = storage.store(ORGANIZATION, PRODUCT, "image/png", CONTENT);

            ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
            verify(s3).putObject(request.capture(), any(RequestBody.class));
            assertEquals(BUCKET, request.getValue().bucket());
            assertEquals(key, request.getValue().key());
            assertEquals("image/png", request.getValue().contentType());
            assertTrue(key.startsWith("organizations/" + ORGANIZATION + "/products/" + PRODUCT + "/"));
            assertTrue(key.endsWith(".png"));
        }

        @Test
        void aClientFilenameNeverBecomesTheKey() {
            String key = storage.store(ORGANIZATION, PRODUCT, "image/webp", CONTENT);

            assertFalse(key.contains("..") || key.contains(" "));
            assertTrue(key.endsWith(".webp"));
        }

        @Test
        void anUnsupportedTypeIsRejectedBeforeAnyUpload() {
            assertThrows(UnsupportedProductImageTypeException.class,
                    () -> storage.store(ORGANIZATION, PRODUCT, "text/html", CONTENT));

            verify(s3, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        }

        @Test
        void anSdkFailureBecomesADomainExceptionWithoutSecrets() {
            when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                    .thenThrow(SdkClientException.create("connection failed with " + SECRET));

            ProductImageStorageException thrown = assertThrows(ProductImageStorageException.class,
                    () -> storage.store(ORGANIZATION, PRODUCT, "image/png", CONTENT));

            assertEquals("Product image storage failed", thrown.getMessage());
            assertTrue(!thrown.getMessage().contains(SECRET));
            assertInstanceOf(SdkClientException.class, thrown.getCause());
        }
    }

    @Nested
    class Delete {

        @Test
        void deletesTheObjectAtTheGivenKey() {
            String key = "organizations/" + ORGANIZATION + "/products/" + PRODUCT + "/a.png";

            storage.delete(key);

            ArgumentCaptor<DeleteObjectRequest> request = ArgumentCaptor.forClass(DeleteObjectRequest.class);
            verify(s3).deleteObject(request.capture());
            assertEquals(BUCKET, request.getValue().bucket());
            assertEquals(key, request.getValue().key());
        }

        @Test
        void anSdkFailureBecomesADomainException() {
            when(s3.deleteObject(any(DeleteObjectRequest.class)))
                    .thenThrow(SdkClientException.create("boom " + SECRET));

            ProductImageStorageException thrown = assertThrows(ProductImageStorageException.class,
                    () -> storage.delete("organizations/x/products/y/z.png"));

            assertFalse(thrown.getMessage().contains(SECRET));
        }
    }

    @Nested
    class PublicUrl {

        @Test
        void joinsThePublicBaseUrlAndTheKey() {
            assertEquals(PUBLIC_BASE_URL + "/organizations/x/products/y/z.png",
                    storage.publicUrl("organizations/x/products/y/z.png"));
        }
    }
}
