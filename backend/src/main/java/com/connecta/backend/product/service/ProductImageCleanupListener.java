package com.connecta.backend.product.service;

import com.connecta.backend.product.repository.ProductImageRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Observer of {@link ProductDeletedEvent}. The image rows vanish with the product (ON DELETE CASCADE), so the
 * object keys are read while the event is handled, inside the delete transaction; the stored objects are removed
 * only after that transaction commits, so a rollback never loses an image. A failed object delete is logged and
 * the remaining objects are still removed.
 */
@Component
@RequiredArgsConstructor
class ProductImageCleanupListener {

    private static final Logger log = LoggerFactory.getLogger(ProductImageCleanupListener.class);

    private final ProductImageRepository images;
    private final ProductImageStorage storage;

    @EventListener
    void onProductDeleted(ProductDeletedEvent event) {
        List<String> keys = images.findObjectKeys(event.productId(), event.organizationId());
        if (keys.isEmpty()) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteObjects(keys);
                }
            });
        } else {
            deleteObjects(keys);
        }
    }

    private void deleteObjects(List<String> keys) {
        for (String key : keys) {
            try {
                storage.delete(key);
            } catch (ProductImageStorageException e) {
                log.error("Could not delete product image object {}", key, e);
            }
        }
    }
}
