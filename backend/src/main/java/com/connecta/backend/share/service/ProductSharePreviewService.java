package com.connecta.backend.share.service;

import com.connecta.backend.inbox.service.ConversationDetailService;
import com.connecta.backend.product.dto.ProductImageResponse;
import com.connecta.backend.product.dto.ProductResponse;
import com.connecta.backend.product.service.ProductImageService;
import com.connecta.backend.product.service.ProductService;
import com.connecta.backend.share.dto.ProductSharePreviewResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Previews what sharing a product into a conversation would send. Any member of the organization may; the
 * conversation and the product are both loaded scoped to the organization, so another tenant's id reads as not found.
 */
@Service
@RequiredArgsConstructor
public class ProductSharePreviewService {

    private final ConversationDetailService conversations;
    private final ProductService products;
    private final ProductImageService images;
    private final ProductMessageComposer composer;

    @Transactional(readOnly = true)
    public ProductSharePreviewResponse preview(String subject, UUID organizationId, UUID conversationId,
            UUID productId) {
        conversations.find(subject, organizationId, conversationId);
        ProductResponse product = products.get(subject, organizationId, productId);
        String imageUrl = images.list(subject, organizationId, productId).stream()
                .findFirst()
                .map(ProductImageResponse::contentPath)
                .orElse(null);
        return ProductSharePreviewResponse.of(productId, composer.compose(product), imageUrl);
    }
}
