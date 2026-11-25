package com.connecta.backend.inbox.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.connecta.backend.config.JwtProperties;
import com.connecta.backend.file.service.PublicBaseUrlMissingException;
import com.connecta.backend.inbox.domain.AttachmentKind;
import com.connecta.backend.inbox.domain.MessageDirection;
import com.connecta.backend.inbox.dto.AttachmentResponse;
import com.connecta.backend.inbox.dto.MessageResponse;
import com.connecta.backend.inbox.service.AttachmentTooLargeException;
import com.connecta.backend.inbox.service.ConversationNotFoundException;
import com.connecta.backend.inbox.service.EmptyAttachmentException;
import com.connecta.backend.inbox.service.OutgoingAttachmentService;
import com.connecta.backend.inbox.service.UnsupportedAttachmentTypeException;
import com.connecta.backend.meta.service.ChannelSendFailedException;
import com.connecta.backend.meta.service.MessagingWindowClosedException;
import com.connecta.backend.meta.service.UnsupportedAttachmentException;
import com.connecta.backend.org.service.NotAMemberException;
import com.connecta.backend.support.MockRepositories;
import com.connecta.backend.support.TestTokens;
import com.connecta.backend.support.TestUsers;
import com.connecta.backend.user.domain.Roles;
import com.connecta.backend.user.domain.User;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

/**
 * Sending a file with the service mocked: 201 with Location and the attachment, ADMIN and AGENT allowed, 401,
 * a non-member and another tenant 404, and the problem detail of every refusal (400, 413, 415, 422, 502).
 */
@SpringBootTest
@AutoConfigureMockMvc
@MockRepositories
class OutgoingAttachmentControllerIntegrationTests {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    private final UUID orgId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();
    private final String messagesUrl = "/api/organizations/" + orgId + "/conversations/" + conversationId
            + "/messages";
    private final String url = messagesUrl + "/attachments";
    private final User agent = TestUsers.user("agent@example.com", Roles.AGENT);
    private final User admin = TestUsers.user("admin@example.com", Roles.ADMIN);
    private final MockMultipartFile png = new MockMultipartFile("file", "cat.png", "image/png", PNG);

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtProperties props;

    @MockitoBean
    OutgoingAttachmentService attachments;

    private RequestBuilder upload(User user, MockMultipartFile file) {
        return multipart(url).file(file).header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(props, user));
    }

    private MessageResponse sent(User by) {
        AttachmentResponse file = new AttachmentResponse(UUID.randomUUID(), AttachmentKind.IMAGE,
                "https://api.example.com/api/files/1", "cat.png", "image/png");
        return new MessageResponse(UUID.randomUUID(), MessageDirection.OUTBOUND, "[Photo]",
                Instant.parse("2026-11-24T10:00:00Z"), by.getId(), by.getFullName(), List.of(file));
    }

    private void refuses(RuntimeException failure) {
        when(attachments.send(any(), any(), any(), any())).thenThrow(failure);
    }

    @Test
    void anAgentSendsAFileAndGets201WithLocationAndTheAttachment() throws Exception {
        MessageResponse response = sent(agent);
        when(attachments.send(eq(agent.getId().toString()), eq(orgId), eq(conversationId),
                argThat(upload -> Arrays.equals(PNG, upload.bytes()) && "image/png".equals(upload.contentType())
                        && "cat.png".equals(upload.fileName())))).thenReturn(response);

        mvc.perform(upload(agent, png))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", messagesUrl + "/" + response.id()))
                .andExpect(jsonPath("$.text").value("[Photo]"))
                .andExpect(jsonPath("$.direction").value("OUTBOUND"))
                .andExpect(jsonPath("$.senderId").value(agent.getId().toString()))
                .andExpect(jsonPath("$.attachments[0].kind").value("IMAGE"))
                .andExpect(jsonPath("$.attachments[0].url").value("https://api.example.com/api/files/1"))
                .andExpect(jsonPath("$.attachments[0].fileName").value("cat.png"))
                .andExpect(jsonPath("$.attachments[0].contentType").value("image/png"));
    }

    @Test
    void anAdminMaySendToo() throws Exception {
        when(attachments.send(eq(admin.getId().toString()), eq(orgId), eq(conversationId), any()))
                .thenReturn(sent(admin));

        mvc.perform(upload(admin, png)).andExpect(status().isCreated());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(multipart(url).file(png)).andExpect(status().isUnauthorized());

        verify(attachments, never()).send(any(), any(), any(), any());
    }

    @Test
    void aMissingFilePartIs400() throws Exception {
        mvc.perform(multipart(url).file(new MockMultipartFile("other", "a.png", "image/png", PNG))
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(props, agent)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));

        verify(attachments, never()).send(any(), any(), any(), any());
    }

    @Test
    void anEmptyFileIs400() throws Exception {
        refuses(new EmptyAttachmentException());

        mvc.perform(upload(agent, png))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The file is empty."));
    }

    @Test
    void aFileOverTheLimitIs413() throws Exception {
        refuses(new AttachmentTooLargeException(8L * 1024 * 1024));

        mvc.perform(upload(agent, png))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.status").value(413))
                .andExpect(jsonPath("$.detail").value("The file is larger than the 8 MB limit."));
    }

    @Test
    void aTypeThatIsNotAllowedIs415() throws Exception {
        refuses(new UnsupportedAttachmentTypeException());

        mvc.perform(upload(agent, png))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail").value(new UnsupportedAttachmentTypeException().getMessage()));
    }

    @Test
    void aKindTheChannelCannotDeliverIs422() throws Exception {
        refuses(new UnsupportedAttachmentException());

        mvc.perform(upload(agent, png))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(new UnsupportedAttachmentException().getMessage()));
    }

    @Test
    void aMissingPublicBaseUrlIs422() throws Exception {
        refuses(new PublicBaseUrlMissingException());

        mvc.perform(upload(agent, png))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(new PublicBaseUrlMissingException().getMessage()));
    }

    @Test
    void aClosedReplyWindowIs422() throws Exception {
        refuses(new MessagingWindowClosedException());

        mvc.perform(upload(agent, png)).andExpect(status().isUnprocessableContent());
    }

    @Test
    void aMetaFailureIs502WithoutMetasDetail() throws Exception {
        refuses(new ChannelSendFailedException("secret detail"));

        mvc.perform(upload(agent, png))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("Meta could not deliver the message. Try again."));
    }

    @Test
    void aNonMemberIsNotFound() throws Exception {
        refuses(new NotAMemberException());

        mvc.perform(upload(agent, png)).andExpect(status().isNotFound());
    }

    @Test
    void anotherTenantsConversationIsNotFound() throws Exception {
        refuses(new ConversationNotFoundException());

        mvc.perform(upload(agent, png))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Conversation not found"));
    }
}
