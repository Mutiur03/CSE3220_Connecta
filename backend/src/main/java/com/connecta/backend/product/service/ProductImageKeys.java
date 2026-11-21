package com.connecta.backend.product.service;

import java.util.Map;
import java.util.UUID;

/**
 * Builds object keys of the form {@code organizations/{orgId}/products/{productId}/{generatedName}}. The name
 * is a random UUID plus an extension taken from the allowed content type, so no client filename reaches storage.
 */
public final class ProductImageKeys {

    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "image/gif", "gif");

    private ProductImageKeys() {
    }

    public static String create(UUID organizationId, UUID productId, String contentType) {
        String extension = extensionFor(contentType);
        return organizationPrefix(organizationId) + "products/" + productId + "/" + UUID.randomUUID() + "." + extension;
    }

    /** The prefix every key of one organization starts with. */
    public static String organizationPrefix(UUID organizationId) {
        return "organizations/" + organizationId + "/";
    }

    private static String extensionFor(String contentType) {
        String extension = contentType == null ? null : EXTENSIONS.get(contentType);
        if (extension == null) {
            throw new UnsupportedProductImageTypeException();
        }
        return extension;
    }
}
