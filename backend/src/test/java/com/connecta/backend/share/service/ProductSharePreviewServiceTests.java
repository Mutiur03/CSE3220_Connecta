package com.connecta.backend.share.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.connecta.backend.inbox.service.ConversationDetailService;
import com.connecta.backend.inbox.service.ConversationNotFoundException;
import com.connecta.backend.product.domain.StockStatus;
import com.connecta.backend.product.dto.ProductImageResponse;
import com.connecta.backend.product.dto.ProductResponse;
import com.connecta.backend.product.service.ProductImageService;
import com.connecta.backend.product.service.ProductNotFoundException;
import com.connecta.backend.product.service.ProductService;
import com.connecta.backend.share.dto.ProductSharePreviewResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductSharePreviewServiceTests {

    private static final String SUBJECT = UUID.randomUUID().toString();
    private final UUID orgId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();

    @Mock
    ConversationDetailService conversations;
    @Mock
    ProductService products;
    @Mock
    ProductImageService images;
    @Spy
    ProductMessageComposer composer = new ProductMessageComposer();
    @InjectMocks
    ProductSharePreviewService service;

    private ProductResponse product() {
        return new ProductResponse(productId, "Desk Lamp", null, null, null, "LAMP-1", new BigDecimal("19.90"), 5,
                0, StockStatus.IN_STOCK, null, null);
    }

    private ProductImageResponse image(String path) {
        return new ProductImageResponse(UUID.randomUUID(), "image/png", 10, 0, path, null);
    }

    @Nested
    class Preview {

        @Test
        void composesTheTextAndUsesTheFirstImage() {
            when(products.get(SUBJECT, orgId, productId)).thenReturn(product());
            when(images.list(SUBJECT, orgId, productId)).thenReturn(List.of(image("/first"), image("/second")));

            ProductSharePreviewResponse preview = service.preview(SUBJECT, orgId, conversationId, productId);

            assertEquals(productId, preview.productId());
            assertEquals("Desk Lamp\nPrice: 19.90\nSKU: LAMP-1\nIn stock", preview.text());
            assertEquals("/first", preview.imageUrl());
        }

        @Test
        void imageUrlIsNullWithoutImages() {
            when(products.get(SUBJECT, orgId, productId)).thenReturn(product());
            when(images.list(SUBJECT, orgId, productId)).thenReturn(List.of());

            assertNull(service.preview(SUBJECT, orgId, conversationId, productId).imageUrl());
        }

        @Test
        void anUnknownConversationStopsBeforeTheProductIsRead() {
            when(conversations.find(SUBJECT, orgId, conversationId)).thenThrow(new ConversationNotFoundException());

            assertThrows(ConversationNotFoundException.class,
                    () -> service.preview(SUBJECT, orgId, conversationId, productId));
            verifyNoInteractions(products, images);
        }

        @Test
        void anUnknownProductIsNotFound() {
            when(products.get(SUBJECT, orgId, productId)).thenThrow(new ProductNotFoundException());

            assertThrows(ProductNotFoundException.class,
                    () -> service.preview(SUBJECT, orgId, conversationId, productId));
            verifyNoInteractions(images);
        }
    }
}
