package com.connecta.backend.product.service;

import java.util.UUID;

/** Published by {@link ProductService#delete} before the product is removed, so listeners can still read its data. */
public record ProductDeletedEvent(UUID organizationId, UUID productId) {
}
