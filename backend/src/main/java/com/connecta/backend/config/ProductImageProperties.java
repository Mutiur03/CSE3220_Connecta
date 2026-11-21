package com.connecta.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Upload limits for product images (app.product-images.*). maxSizeBytes is the largest accepted file,
 * maxPerProduct the most images one product may have. A missing or non-positive value falls back to the default.
 */
@ConfigurationProperties(prefix = "app.product-images")
public record ProductImageProperties(long maxSizeBytes, int maxPerProduct) {

    static final long DEFAULT_MAX_SIZE_BYTES = 5L * 1024 * 1024;
    static final int DEFAULT_MAX_PER_PRODUCT = 8;

    public ProductImageProperties {
        maxSizeBytes = maxSizeBytes > 0 ? maxSizeBytes : DEFAULT_MAX_SIZE_BYTES;
        maxPerProduct = maxPerProduct > 0 ? maxPerProduct : DEFAULT_MAX_PER_PRODUCT;
    }
}
