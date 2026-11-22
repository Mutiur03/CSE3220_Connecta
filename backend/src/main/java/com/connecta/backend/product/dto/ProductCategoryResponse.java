package com.connecta.backend.product.dto;

import com.connecta.backend.product.domain.ProductCategory;
import java.time.Instant;
import java.util.UUID;

/** A product category as the API returns it. Never exposes the entity itself. */
public record ProductCategoryResponse(
        UUID id,
        UUID organizationId,
        String name,
        String description,
        Instant createdAt,
        Instant updatedAt) {

    public static ProductCategoryResponse from(ProductCategory category) {
        return new ProductCategoryResponse(
                category.getId(),
                category.getOrganization().getId(),
                category.getName(),
                category.getDescription(),
                category.getCreatedAt(),
                category.getUpdatedAt());
    }
}
