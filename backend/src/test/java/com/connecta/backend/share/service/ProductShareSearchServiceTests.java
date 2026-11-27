package com.connecta.backend.share.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.connecta.backend.common.dto.PageResponse;
import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.inbox.domain.Conversation;
import com.connecta.backend.inbox.service.ConversationDetailService;
import com.connecta.backend.inbox.service.ConversationNotFoundException;
import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.product.domain.StockStatus;
import com.connecta.backend.product.dto.ProductResponse;
import com.connecta.backend.product.service.ProductFilter;
import com.connecta.backend.product.service.ProductImageService;
import com.connecta.backend.product.service.ProductService;
import com.connecta.backend.share.dto.ProductShareCandidateResponse;
import com.connecta.backend.support.TestUsers;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductShareSearchServiceTests {

    @Mock
    ConversationDetailService conversations;

    @Mock
    ProductService products;

    @Mock
    ProductImageService images;

    ProductShareSearchService service;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();
    private final String subject = UUID.randomUUID().toString();
    private final Organization organization = new Organization("Acme", "acme");
    private final Conversation conversation = new Conversation(organization, Channel.MESSENGER, "thread-1");

    @BeforeEach
    void setUp() {
        TestUsers.set(organization, "id", organizationId);
        service = new ProductShareSearchService(conversations, products, images);
    }

    private ProductResponse product(String name, String sku) {
        return new ProductResponse(UUID.randomUUID(), name, null, null, null, sku, new BigDecimal("9.50"), 5, 2,
                StockStatus.IN_STOCK, null, null);
    }

    @Nested
    class Search {

        @Test
        void filtersByTextInTheConversationsOrganizationAndAttachesFirstImages() {
            ProductResponse shoes = product("Shoes", "SH-1");
            ProductResponse hat = product("Hat", null);
            when(conversations.find(subject, organizationId, conversationId)).thenReturn(conversation);
            when(products.list(any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(new PageResponse<>(List.of(shoes, hat), 1, 5, 12));
            when(images.firstImagePaths(subject, organizationId, List.of(shoes.id(), hat.id())))
                    .thenReturn(Map.of(shoes.id(), "/img/shoes"));

            PageResponse<ProductShareCandidateResponse> result =
                    service.search(subject, organizationId, conversationId, " sh ", 1, 5);

            ArgumentCaptor<ProductFilter> filter = forClass(ProductFilter.class);
            verify(products).list(any(), any(), filter.capture(), anyInt(), anyInt());
            assertEquals(" sh ", filter.getValue().text());
            assertEquals(1, result.page());
            assertEquals(5, result.size());
            assertEquals(12, result.totalElements());
            assertEquals(shoes.id(), result.content().get(0).id());
            assertEquals("SH-1", result.content().get(0).sku());
            assertEquals(new BigDecimal("9.50"), result.content().get(0).unitPrice());
            assertEquals(StockStatus.IN_STOCK, result.content().get(0).stockStatus());
            assertEquals("/img/shoes", result.content().get(0).imageUrl());
            assertNull(result.content().get(1).imageUrl());
        }

        @Test
        void anEmptyPageHasNoCandidates() {
            when(conversations.find(subject, organizationId, conversationId)).thenReturn(conversation);
            when(products.list(any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(new PageResponse<>(List.of(), 0, 20, 0));
            when(images.firstImagePaths(subject, organizationId, List.of())).thenReturn(Map.of());

            assertEquals(List.of(), service.search(subject, organizationId, conversationId, null, 0, 20).content());
        }

        @Test
        void aConversationThatIsNotFoundStopsBeforeAnyProductLookup() {
            when(conversations.find(subject, organizationId, conversationId))
                    .thenThrow(new ConversationNotFoundException());

            assertThrows(ConversationNotFoundException.class,
                    () -> service.search(subject, organizationId, conversationId, "x", 0, 20));

            verifyNoInteractions(products, images);
        }
    }
}
