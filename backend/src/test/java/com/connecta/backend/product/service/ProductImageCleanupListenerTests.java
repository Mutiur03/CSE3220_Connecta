package com.connecta.backend.product.service;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** The phase (after commit only) is proven by ProductImageCleanupListenerIntegrationTests against a real rollback. */
@ExtendWith(MockitoExtension.class)
class ProductImageCleanupListenerTests {

    private final UUID organizationId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();

    @Mock
    ProductImageStorage storage;

    @InjectMocks
    ProductImageCleanupListener listener;

    @Nested
    class OnProductDeleted {

        @Test
        void everyCarriedObjectIsDeleted() {
            listener.onProductDeleted(new ProductDeletedEvent(organizationId, productId, List.of("k1", "k2")));

            verify(storage).delete("k1");
            verify(storage).delete("k2");
        }

        @Test
        void aFailedObjectDeleteDoesNotStopTheOthers() {
            doThrow(new ProductImageStorageException(new RuntimeException("down"))).when(storage).delete("k1");

            listener.onProductDeleted(new ProductDeletedEvent(organizationId, productId, List.of("k1", "k2")));

            verify(storage).delete("k2");
        }

        @Test
        void aProductWithoutImagesTouchesNothing() {
            listener.onProductDeleted(new ProductDeletedEvent(organizationId, productId, List.of()));

            verify(storage, never()).delete(anyString());
        }
    }
}
