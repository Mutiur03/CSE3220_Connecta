package com.connecta.backend.product.service;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.connecta.backend.product.repository.ProductImageRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class ProductImageCleanupListenerTests {

    private final UUID organizationId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();
    private final ProductDeletedEvent event = new ProductDeletedEvent(organizationId, productId);

    @Mock
    ProductImageRepository images;

    @Mock
    ProductImageStorage storage;

    @InjectMocks
    ProductImageCleanupListener listener;

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private void commit() {
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }
    }

    @Nested
    class InsideATransaction {

        @Test
        void objectsAreDeletedOnlyAfterTheCommit() {
            TransactionSynchronizationManager.initSynchronization();
            when(images.findObjectKeys(productId, organizationId)).thenReturn(List.of("k1", "k2"));

            listener.onProductDeleted(event);
            verify(storage, never()).delete(anyString());

            commit();
            verify(storage).delete("k1");
            verify(storage).delete("k2");
        }

        @Test
        void aRolledBackDeleteLeavesTheObjectsAlone() {
            TransactionSynchronizationManager.initSynchronization();
            when(images.findObjectKeys(productId, organizationId)).thenReturn(List.of("k1"));

            listener.onProductDeleted(event);
            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            }

            verify(storage, never()).delete(anyString());
        }

        @Test
        void aFailedObjectDeleteDoesNotStopTheOthers() {
            TransactionSynchronizationManager.initSynchronization();
            when(images.findObjectKeys(productId, organizationId)).thenReturn(List.of("k1", "k2"));
            doThrow(new ProductImageStorageException(new RuntimeException("down"))).when(storage).delete("k1");

            listener.onProductDeleted(event);
            commit();

            verify(storage).delete("k2");
        }

        @Test
        void aProductWithoutImagesTouchesNothing() {
            TransactionSynchronizationManager.initSynchronization();
            when(images.findObjectKeys(productId, organizationId)).thenReturn(List.of());

            listener.onProductDeleted(event);
            commit();

            verify(storage, never()).delete(anyString());
        }
    }

    @Nested
    class OutsideATransaction {

        @Test
        void objectsAreDeletedRightAway() {
            when(images.findObjectKeys(productId, organizationId)).thenReturn(List.of("k1"));

            listener.onProductDeleted(event);

            verify(storage).delete("k1");
        }
    }
}
