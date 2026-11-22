package com.connecta.backend.product.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Observer of {@link ProductDeletedEvent}. Runs only after the delete transaction commits, so a rollback never
 * removes a stored object. A failed object delete is logged and the remaining objects are still removed.
 */
@Component
@RequiredArgsConstructor
class ProductImageCleanupListener {

    private static final Logger log = LoggerFactory.getLogger(ProductImageCleanupListener.class);

    private final ProductImageStorage storage;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onProductDeleted(ProductDeletedEvent event) {
        for (String key : event.objectKeys()) {
            try {
                storage.delete(key);
            } catch (ProductImageStorageException e) {
                log.error("Could not delete product image object {}", key, e);
            }
        }
    }
}
