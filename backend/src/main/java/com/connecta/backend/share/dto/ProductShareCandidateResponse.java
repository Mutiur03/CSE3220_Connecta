package com.connecta.backend.share.dto;

import com.connecta.backend.product.domain.StockStatus;
import com.connecta.backend.product.dto.ProductResponse;
import java.math.BigDecimal;
import java.util.UUID;

/** A product an agent may share in a conversation. {@code imageUrl} is the first image's content path, or null. */
public record ProductShareCandidateResponse(
        UUID id,
        String name,
        String sku,
        BigDecimal unitPrice,
        StockStatus stockStatus,
        String imageUrl) {

    public static ProductShareCandidateResponse from(ProductResponse product, String imageUrl) {
        return new ProductShareCandidateResponse(product.id(), product.name(), product.sku(), product.unitPrice(),
                product.stockStatus(), imageUrl);
    }
}
