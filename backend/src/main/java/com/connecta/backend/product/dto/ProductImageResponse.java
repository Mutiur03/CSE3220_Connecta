package com.connecta.backend.product.dto;

import com.connecta.backend.product.domain.ProductImage;
import java.time.Instant;
import java.util.UUID;

/** An image as the API shows it. {@code contentPath} is the authenticated URL path that streams the bytes. */
public record ProductImageResponse(
        UUID id,
        String contentType,
        long sizeBytes,
        int position,
        String contentPath,
        Instant createdAt) {

    public static ProductImageResponse from(ProductImage image) {
        String path = "/api/organizations/" + image.getOrganization().getId() + "/products/"
                + image.getProduct().getId() + "/images/" + image.getId() + "/content";
        return new ProductImageResponse(image.getId(), image.getContentType(), image.getSizeBytes(),
                image.getPosition(), path, image.getCreatedAt());
    }
}
