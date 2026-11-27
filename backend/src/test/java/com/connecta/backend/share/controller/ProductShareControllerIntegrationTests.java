package com.connecta.backend.share.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.connecta.backend.config.JwtProperties;
import com.connecta.backend.inbox.domain.MessageDirection;
import com.connecta.backend.inbox.dto.MessageResponse;
import com.connecta.backend.inbox.dto.SendMessageRequest;
import com.connecta.backend.inbox.service.ConversationNotFoundException;
import com.connecta.backend.meta.service.ChannelNotConnectedException;
import com.connecta.backend.meta.service.ChannelSendFailedException;
import com.connecta.backend.org.service.NotAMemberException;
import com.connecta.backend.product.service.ProductNotFoundException;
import com.connecta.backend.share.dto.ProductShareResponse;
import com.connecta.backend.share.service.ProductShareService;
import com.connecta.backend.support.MockRepositories;
import com.connecta.backend.support.TestTokens;
import com.connecta.backend.support.TestUsers;
import com.connecta.backend.user.domain.Roles;
import com.connecta.backend.user.domain.User;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Sharing a product with the service mocked: 201 body, 401, ADMIN and AGENT allowed, 400, 404s, 422 and 502. */
@SpringBootTest
@AutoConfigureMockMvc
@MockRepositories
class ProductShareControllerIntegrationTests {

    private final UUID orgId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();
    private final String url = "/api/organizations/" + orgId + "/conversations/" + conversationId
            + "/product-shares";
    private final User agent = TestUsers.user("agent@example.com", Roles.AGENT);
    private final User admin = TestUsers.user("admin@example.com", Roles.ADMIN);

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtProperties props;

    @MockitoBean
    ProductShareService shares;

    private String body(String text) {
        return "{\"productId\":\"" + productId + "\",\"text\":\"" + text + "\"}";
    }

    private MockHttpServletRequestBuilder share(User user, String json) {
        return post(url).header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(props, user))
                .contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private ProductShareResponse sent(String... texts) {
        return new ProductShareResponse(java.util.Arrays.stream(texts).map(text -> new MessageResponse(
                UUID.randomUUID(), MessageDirection.OUTBOUND, text, Instant.now(), null, null, List.of())).toList());
    }

    @Test
    void anAgentSharesAndGetsTheMessagesBack() throws Exception {
        when(shares.share(agent.getId().toString(), orgId, conversationId, productId, "Desk Lamp"))
                .thenReturn(sent("[Photo]", "Desk Lamp"));

        mvc.perform(share(agent, body("Desk Lamp")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.messages.length()").value(2))
                .andExpect(jsonPath("$.messages[0].text").value("[Photo]"))
                .andExpect(jsonPath("$.messages[1].text").value("Desk Lamp"));
    }

    @Test
    void anAdminMayShareToo() throws Exception {
        when(shares.share(admin.getId().toString(), orgId, conversationId, productId, "Desk Lamp"))
                .thenReturn(sent("Desk Lamp"));

        mvc.perform(share(admin, body("Desk Lamp")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.messages.length()").value(1));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body("Hi")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aMissingProductIdIs400() throws Exception {
        mvc.perform(share(agent, "{\"text\":\"Hi\"}")).andExpect(status().isBadRequest());
    }

    @Test
    void aBlankTextIs400() throws Exception {
        mvc.perform(share(agent, body(" "))).andExpect(status().isBadRequest());
    }

    @Test
    void aTooLongTextIs400() throws Exception {
        mvc.perform(share(agent, body("a".repeat(SendMessageRequest.TEXT_MAX_LENGTH + 1))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aNonMemberIsNotFound() throws Exception {
        when(shares.share(any(), any(), any(), any(), any())).thenThrow(new NotAMemberException());

        mvc.perform(share(agent, body("Hi"))).andExpect(status().isNotFound());
    }

    @Test
    void anotherTenantsConversationIsNotFound() throws Exception {
        when(shares.share(any(), any(), any(), any(), any())).thenThrow(new ConversationNotFoundException());

        mvc.perform(share(agent, body("Hi"))).andExpect(status().isNotFound());
    }

    @Test
    void anotherTenantsProductIsNotFound() throws Exception {
        when(shares.share(any(), any(), any(), any(), any())).thenThrow(new ProductNotFoundException());

        mvc.perform(share(agent, body("Hi"))).andExpect(status().isNotFound());
    }

    @Test
    void aChannelThatIsNotConnectedIs422() throws Exception {
        when(shares.share(any(), any(), any(), any(), any())).thenThrow(new ChannelNotConnectedException());

        mvc.perform(share(agent, body("Hi"))).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void aMetaFailureIs502() throws Exception {
        when(shares.share(any(), any(), any(), any(), any())).thenThrow(new ChannelSendFailedException("meta"));

        mvc.perform(share(agent, body("Hi"))).andExpect(status().isBadGateway());
    }
}
