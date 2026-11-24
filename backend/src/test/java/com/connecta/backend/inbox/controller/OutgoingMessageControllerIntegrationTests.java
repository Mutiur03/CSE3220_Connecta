package com.connecta.backend.inbox.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.connecta.backend.config.JwtProperties;
import com.connecta.backend.inbox.domain.MessageDirection;
import com.connecta.backend.inbox.dto.MessageResponse;
import com.connecta.backend.inbox.service.ConversationNotFoundException;
import com.connecta.backend.inbox.service.OutgoingMessageService;
import com.connecta.backend.meta.service.ChannelNotConnectedException;
import com.connecta.backend.meta.service.ChannelSendFailedException;
import com.connecta.backend.meta.service.MessagingWindowClosedException;
import com.connecta.backend.org.service.NotAMemberException;
import com.connecta.backend.support.MockRepositories;
import com.connecta.backend.support.TestTokens;
import com.connecta.backend.support.TestUsers;
import com.connecta.backend.user.domain.Roles;
import com.connecta.backend.user.domain.User;
import java.time.Instant;
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

/**
 * Sending a reply with the service mocked: 201 with Location and body, validation, 401, ADMIN and AGENT
 * allowed, a non-member and another tenant 404, and the problem details of each Meta failure.
 */
@SpringBootTest
@AutoConfigureMockMvc
@MockRepositories
class OutgoingMessageControllerIntegrationTests {

    private final UUID orgId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();
    private final String url = "/api/organizations/" + orgId + "/conversations/" + conversationId + "/messages";
    private final User agent = TestUsers.user("agent@example.com", Roles.AGENT);
    private final User admin = TestUsers.user("admin@example.com", Roles.ADMIN);

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtProperties props;

    @MockitoBean
    OutgoingMessageService outgoing;

    private MockHttpServletRequestBuilder send(User user, String json) {
        return post(url).header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(props, user))
                .contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private MessageResponse sent(User by) {
        return new MessageResponse(UUID.randomUUID(), MessageDirection.OUTBOUND, "hello",
                Instant.parse("2026-11-24T10:00:00Z"), by.getId(), by.getFullName());
    }

    @Test
    void anAgentSendsAndGets201WithLocationAndTheMessage() throws Exception {
        MessageResponse response = sent(agent);
        when(outgoing.send(agent.getId().toString(), orgId, conversationId, "hello")).thenReturn(response);

        mvc.perform(send(agent, "{\"text\":\"hello\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", url + "/" + response.id()))
                .andExpect(jsonPath("$.id").value(response.id().toString()))
                .andExpect(jsonPath("$.direction").value("OUTBOUND"))
                .andExpect(jsonPath("$.text").value("hello"))
                .andExpect(jsonPath("$.senderId").value(agent.getId().toString()))
                .andExpect(jsonPath("$.sentAt").exists());
    }

    @Test
    void anAdminMaySendToo() throws Exception {
        when(outgoing.send(admin.getId().toString(), orgId, conversationId, "hello")).thenReturn(sent(admin));

        mvc.perform(send(admin, "{\"text\":\"hello\"}")).andExpect(status().isCreated());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"hello\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsBlankText() throws Exception {
        mvc.perform(send(agent, "{\"text\":\"   \"}")).andExpect(status().isBadRequest());

        verify(outgoing, never()).send(any(), any(), any(), any());
    }

    @Test
    void rejectsMissingText() throws Exception {
        mvc.perform(send(agent, "{}")).andExpect(status().isBadRequest());
    }

    @Test
    void rejectsTextOverTheLimit() throws Exception {
        mvc.perform(send(agent, "{\"text\":\"" + "a".repeat(1001) + "\"}")).andExpect(status().isBadRequest());

        verify(outgoing, never()).send(any(), any(), any(), any());
    }

    @Test
    void aNonMemberIsNotFound() throws Exception {
        when(outgoing.send(any(), any(), any(), any())).thenThrow(new NotAMemberException());

        mvc.perform(send(agent, "{\"text\":\"hello\"}")).andExpect(status().isNotFound());
    }

    @Test
    void anotherTenantsConversationIsNotFound() throws Exception {
        when(outgoing.send(any(), any(), any(), any())).thenThrow(new ConversationNotFoundException());

        mvc.perform(send(agent, "{\"text\":\"hello\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Conversation not found"));
    }

    @Test
    void aChannelThatIsNotConnectedIs422() throws Exception {
        when(outgoing.send(any(), any(), any(), any())).thenThrow(new ChannelNotConnectedException());

        mvc.perform(send(agent, "{\"text\":\"hello\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.status").value(422));
    }

    @Test
    void aClosedReplyWindowIs422() throws Exception {
        when(outgoing.send(any(), any(), any(), any())).thenThrow(new MessagingWindowClosedException());

        mvc.perform(send(agent, "{\"text\":\"hello\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(new MessagingWindowClosedException().getMessage()));
    }

    @Test
    void aMetaFailureIs502WithoutMetasDetail() throws Exception {
        when(outgoing.send(any(), any(), any(), any())).thenThrow(new ChannelSendFailedException("secret detail"));

        mvc.perform(send(agent, "{\"text\":\"hello\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.detail").value("Meta could not deliver the message. Try again."));
    }
}
