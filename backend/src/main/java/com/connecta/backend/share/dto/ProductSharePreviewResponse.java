package com.connecta.backend.share.dto;

import java.util.UUID;

/** The message an agent is about to share. {@code imageUrl} is the product's first image, null when it has none. */
public record ProductSharePreviewResponse(UUID productId, String text, String imageUrl) {

    public static ProductSharePreviewResponse of(UUID productId, String text, String imageUrl) {
        return new ProductSharePreviewResponse(productId, text, imageUrl);
    }
}
