package com.connecta.backend.share.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.connecta.backend.common.dto.PageResponse;
import com.connecta.backend.config.JwtProperties;
import com.connecta.backend.inbox.service.ConversationNotFoundException;
import com.connecta.backend.org.service.NotAMemberException;
import com.connecta.backend.product.domain.StockStatus;
import com.connecta.backend.share.dto.ProductShareCandidateResponse;
import com.connecta.backend.share.service.ProductShareSearchService;
import com.connecta.backend.support.MockRepositories;
import com.connecta.backend.support.TestTokens;
import com.connecta.backend.support.TestUsers;
import com.connecta.backend.user.domain.Roles;
import com.connecta.backend.user.domain.User;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Product search inside a conversation with the service mocked: 200 body, paging 400, 401, roles, 404s. */
@SpringBootTest
@AutoConfigureMockMvc
@MockRepositories
class ProductShareSearchControllerIntegrationTests {

    private final UUID orgId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();
    private final String url = "/api/organizations/" + orgId + "/conversations/" + conversationId
            + "/product-search";
    private final User agent = TestUsers.user("agent@example.com", Roles.AGENT);
    private final User admin = TestUsers.user("admin@example.com", Roles.ADMIN);

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtProperties props;

    @MockitoBean
    ProductShareSearchService search;

    private MockHttpServletRequestBuilder search(User user, String query) {
        return get(url + query).header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(props, user));
    }

    private PageResponse<ProductShareCandidateResponse> onePage(UUID productId) {
        return new PageResponse<>(List.of(new ProductShareCandidateResponse(productId, "Shoes", "SH-1",
                new BigDecimal("9.50"), StockStatus.IN_STOCK, "/img/1")), 0, 20, 1);
    }

    @Test
    void anAgentSearchesAndGetsThePage() throws Exception {
        UUID productId = UUID.randomUUID();
        when(search.search(agent.getId().toString(), orgId, conversationId, "sh", 0, 20))
                .thenReturn(onePage(productId));

        mvc.perform(search(agent, "?q=sh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(productId.toString()))
                .andExpect(jsonPath("$.content[0].name").value("Shoes"))
                .andExpect(jsonPath("$.content[0].sku").value("SH-1"))
                .andExpect(jsonPath("$.content[0].unitPrice").value(9.5))
                .andExpect(jsonPath("$.content[0].stockStatus").value("IN_STOCK"))
                .andExpect(jsonPath("$.content[0].imageUrl").value("/img/1"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void anAdminMaySearchToo() throws Exception {
        when(search.search(admin.getId().toString(), orgId, conversationId, null, 2, 5))
                .thenReturn(new PageResponse<>(List.of(), 2, 5, 0));

        mvc.perform(search(admin, "?page=2&size=5")).andExpect(status().isOk());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get(url)).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsPagingOutOfRange() throws Exception {
        mvc.perform(search(agent, "?size=101")).andExpect(status().isBadRequest());
        mvc.perform(search(agent, "?size=0")).andExpect(status().isBadRequest());
        mvc.perform(search(agent, "?page=-1")).andExpect(status().isBadRequest());

        verify(search, never()).search(any(), any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void aNonMemberIsNotFound() throws Exception {
        when(search.search(any(), any(), any(), any(), anyInt(), anyInt())).thenThrow(new NotAMemberException());

        mvc.perform(search(agent, "")).andExpect(status().isNotFound());
    }

    @Test
    void anotherTenantsConversationIsNotFound() throws Exception {
        when(search.search(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenThrow(new ConversationNotFoundException());

        mvc.perform(search(agent, ""))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Conversation not found"));
    }
}
