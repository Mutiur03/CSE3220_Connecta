package com.connecta.backend.share.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.connecta.backend.config.JwtProperties;
import com.connecta.backend.inbox.service.ConversationNotFoundException;
import com.connecta.backend.org.service.NotAMemberException;
import com.connecta.backend.product.service.ProductNotFoundException;
import com.connecta.backend.share.dto.ProductSharePreviewResponse;
import com.connecta.backend.share.service.ProductSharePreviewService;
import com.connecta.backend.support.MockRepositories;
import com.connecta.backend.support.TestTokens;
import com.connecta.backend.support.TestUsers;
import com.connecta.backend.user.domain.Roles;
import com.connecta.backend.user.domain.User;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** The share preview with the service mocked: 200 body, 401, ADMIN and AGENT allowed, 400, and every 404 case. */
@SpringBootTest
@AutoConfigureMockMvc
@MockRepositories
class ProductSharePreviewControllerIntegrationTests {

    private final UUID orgId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();
    private final String url = "/api/organizations/" + orgId + "/conversations/" + conversationId
            + "/product-shares/preview";
    private final User agent = TestUsers.user("agent@example.com", Roles.AGENT);
    private final User admin = TestUsers.user("admin@example.com", Roles.ADMIN);

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtProperties props;

    @MockitoBean
    ProductSharePreviewService previews;

    private MockHttpServletRequestBuilder preview(User user, String query) {
        return get(url + query).header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(props, user));
    }

    @Test
    void anAgentGetsThePreview() throws Exception {
        when(previews.preview(agent.getId().toString(), orgId, conversationId, productId))
                .thenReturn(new ProductSharePreviewResponse(productId, "Desk Lamp", "/img"));

        mvc.perform(preview(agent, "?productId=" + productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(productId.toString()))
                .andExpect(jsonPath("$.text").value("Desk Lamp"))
                .andExpect(jsonPath("$.imageUrl").value("/img"));
    }

    @Test
    void anAdminMayPreviewToo() throws Exception {
        when(previews.preview(admin.getId().toString(), orgId, conversationId, productId))
                .thenReturn(new ProductSharePreviewResponse(productId, "Desk Lamp", null));

        mvc.perform(preview(admin, "?productId=" + productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").doesNotExist());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get(url + "?productId=" + productId)).andExpect(status().isUnauthorized());
    }

    @Test
    void aMissingProductIdIs400() throws Exception {
        mvc.perform(preview(agent, "")).andExpect(status().isBadRequest());
    }

    @Test
    void aMalformedProductIdIs400() throws Exception {
        mvc.perform(preview(agent, "?productId=nope")).andExpect(status().isBadRequest());
    }

    @Test
    void aNonMemberIsNotFound() throws Exception {
        when(previews.preview(any(), any(), any(), any())).thenThrow(new NotAMemberException());

        mvc.perform(preview(agent, "?productId=" + productId)).andExpect(status().isNotFound());
    }

    @Test
    void anotherTenantsConversationIsNotFound() throws Exception {
        when(previews.preview(any(), any(), any(), any())).thenThrow(new ConversationNotFoundException());

        mvc.perform(preview(agent, "?productId=" + productId)).andExpect(status().isNotFound());
    }

    @Test
    void anotherTenantsProductIsNotFound() throws Exception {
        when(previews.preview(any(), any(), any(), any())).thenThrow(new ProductNotFoundException());

        mvc.perform(preview(agent, "?productId=" + productId)).andExpect(status().isNotFound());
    }
}
