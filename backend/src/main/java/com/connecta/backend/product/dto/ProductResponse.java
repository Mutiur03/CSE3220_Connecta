package com.connecta.backend.product.dto;

import com.connecta.backend.product.domain.Product;
import com.connecta.backend.product.domain.ProductCategory;
import java.time.Instant;
import java.util.UUID;

/** A product as the API shows it. The category fields are null when the product has none. */
public record ProductResponse(
        UUID id,
        String name,
        String description,
        UUID categoryId,
        String categoryName,
        Instant createdAt,
        Instant updatedAt) {

    public static ProductResponse from(Product product) {
        ProductCategory category = product.getCategory();
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                category == null ? null : category.getId(),
                category == null ? null : category.getName(),
                product.getCreatedAt(),
                product.getUpdatedAt());
    }
}
