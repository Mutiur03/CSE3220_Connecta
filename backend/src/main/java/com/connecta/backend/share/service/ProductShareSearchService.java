package com.connecta.backend.share.service;

import com.connecta.backend.common.dto.PageResponse;
import com.connecta.backend.inbox.domain.Conversation;
import com.connecta.backend.inbox.service.ConversationDetailService;
import com.connecta.backend.product.dto.ProductResponse;
import com.connecta.backend.product.service.ProductFilter;
import com.connecta.backend.product.service.ProductImageService;
import com.connecta.backend.product.service.ProductService;
import com.connecta.backend.share.dto.ProductShareCandidateResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Finds products to share in a conversation. Membership and conversation ownership are checked by
 * {@link ConversationDetailService} (another tenant's conversation is not found); the products come from the
 * conversation's own organization. First images are fetched for the whole page in one query.
 */
@Service
@RequiredArgsConstructor
public class ProductShareSearchService {

    private final ConversationDetailService conversations;
    private final ProductService products;
    private final ProductImageService images;

    public PageResponse<ProductShareCandidateResponse> search(String subject, UUID organizationId,
            UUID conversationId, String text, int page, int size) {
        Conversation conversation = conversations.find(subject, organizationId, conversationId);
        UUID orgId = conversation.getOrganization().getId();
        PageResponse<ProductResponse> found = products.list(subject, orgId, new ProductFilter(text, null, null),
                page, size);
        List<UUID> ids = found.content().stream().map(ProductResponse::id).toList();
        Map<UUID, String> imagePaths = images.firstImagePaths(subject, orgId, ids);
        List<ProductShareCandidateResponse> content = found.content().stream()
                .map(product -> ProductShareCandidateResponse.from(product, imagePaths.get(product.id())))
                .toList();
        return new PageResponse<>(content, found.page(), found.size(), found.totalElements());
    }
}
