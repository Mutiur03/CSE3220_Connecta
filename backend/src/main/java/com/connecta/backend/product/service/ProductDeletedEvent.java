package com.connecta.backend.product.service;

import java.util.List;
import java.util.UUID;

/**
 * Published by {@link ProductService#delete} inside the delete transaction. The object keys are read before the
 * delete, because the image rows are gone once it commits; listeners act on these keys only after the commit.
 */
public record ProductDeletedEvent(UUID organizationId, UUID productId, List<String> objectKeys) {

    public ProductDeletedEvent {
        objectKeys = List.copyOf(objectKeys);
    }
}
