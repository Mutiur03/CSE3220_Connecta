package com.connecta.backend.inbox.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.connecta.backend.common.dto.PageResponse;
import com.connecta.backend.config.JwtProperties;
import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.inbox.domain.ConversationStatus;
import com.connecta.backend.inbox.domain.MessageDirection;
import com.connecta.backend.inbox.dto.ConversationDetailResponse;
import com.connecta.backend.inbox.dto.MessageResponse;
import com.connecta.backend.inbox.dto.ParticipantResponse;
import com.connecta.backend.inbox.service.ConversationDetailService;
import com.connecta.backend.inbox.service.ConversationNotFoundException;
import com.connecta.backend.inbox.service.MessageHistoryService;
import com.connecta.backend.org.service.NotAMemberException;
import com.connecta.backend.support.MockRepositories;
import com.connecta.backend.support.TestTokens;
import com.connecta.backend.support.TestUsers;
import com.connecta.backend.user.domain.Roles;
import com.connecta.backend.user.domain.User;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
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
 * The conversation detail endpoints with the services mocked: status codes, bodies, validation, 401 and
 * the problem details each exception maps to. ADMIN and AGENT members are allowed; a non-member (which is
 * also how another tenant reads) is 404.
 */
@SpringBootTest
@AutoConfigureMockMvc
@MockRepositories
class ConversationDetailControllerIntegrationTests {

    private final UUID orgId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();
    private final String base = "/api/organizations/" + orgId + "/conversations/" + conversationId;
    private final Instant now = Instant.parse("2026-11-24T10:00:00Z");

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtProperties props;

    @MockitoBean
    ConversationDetailService conversations;

    @MockitoBean
    MessageHistoryService history;

    private final User agent = TestUsers.user("agent@example.com", Roles.AGENT);
    private final User admin = TestUsers.user("admin@example.com", Roles.ADMIN);

    private String bearer(User user) {
        return TestTokens.bearer(props, user);
    }

    private ConversationDetailResponse detail(ConversationStatus status) {
        return new ConversationDetailResponse(conversationId, orgId, Channel.MESSENGER, status, false,
                List.of(new ParticipantResponse(UUID.randomUUID(), "psid", "Rina", "https://cdn.example/r.jpg")), now, now, now);
    }

    @Nested
    class GetDetail {

        @Test
        void anAgentGetsTheConversation() throws Exception {
            when(conversations.get(agent.getId().toString(), orgId, conversationId))
                    .thenReturn(detail(ConversationStatus.OPEN));

            mvc.perform(get(base).header(HttpHeaders.AUTHORIZATION, bearer(agent)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(conversationId.toString()))
                    .andExpect(jsonPath("$.channel").value("MESSENGER"))
                    .andExpect(jsonPath("$.status").value("OPEN"))
                    .andExpect(jsonPath("$.participants[0].displayName").value("Rina"))
                    .andExpect(jsonPath("$.participants[0].avatarUrl").value("https://cdn.example/r.jpg"))
                    .andExpect(jsonPath("$.lastMessageAt").exists());
        }

        @Test
        void anAdminGetsTheConversation() throws Exception {
            when(conversations.get(admin.getId().toString(), orgId, conversationId))
                    .thenReturn(detail(ConversationStatus.OPEN));

            mvc.perform(get(base).header(HttpHeaders.AUTHORIZATION, bearer(admin))).andExpect(status().isOk());
        }

        @Test
        void requiresAuthentication() throws Exception {
            mvc.perform(get(base)).andExpect(status().isUnauthorized());
        }

        @Test
        void aNonMemberIsNotFound() throws Exception {
            when(conversations.get(any(), any(), any())).thenThrow(new NotAMemberException());

            mvc.perform(get(base).header(HttpHeaders.AUTHORIZATION, bearer(agent)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404));
        }

        @Test
        void anotherTenantsOrUnknownConversationIsNotFound() throws Exception {
            when(conversations.get(any(), any(), any())).thenThrow(new ConversationNotFoundException());

            mvc.perform(get(base).header(HttpHeaders.AUTHORIZATION, bearer(agent)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.detail").value("Conversation not found"));
        }
    }

    @Nested
    class Messages {

        @Test
        void returnsOnePageWithTheDefaultPaging() throws Exception {
            MessageResponse message = new MessageResponse(UUID.randomUUID(), MessageDirection.INBOUND, "hello",
                    now, UUID.randomUUID(), "Rina");
            when(history.list(agent.getId().toString(), orgId, conversationId, 0, 20))
                    .thenReturn(new PageResponse<>(List.of(message), 0, 20, 1));

            mvc.perform(get(base + "/messages").header(HttpHeaders.AUTHORIZATION, bearer(agent)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].direction").value("INBOUND"))
                    .andExpect(jsonPath("$.content[0].text").value("hello"))
                    .andExpect(jsonPath("$.content[0].senderName").value("Rina"))
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.size").value(20))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        void passesPageAndSizeThrough() throws Exception {
            when(history.list(any(), any(), any(), eq(3), eq(100)))
                    .thenReturn(new PageResponse<>(List.of(), 3, 100, 0));

            mvc.perform(get(base + "/messages?page=3&size=100").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.page").value(3));
        }

        @Test
        void rejectsASizeOverTheMaximum() throws Exception {
            mvc.perform(get(base + "/messages?size=101").header(HttpHeaders.AUTHORIZATION, bearer(agent)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void rejectsANegativePage() throws Exception {
            mvc.perform(get(base + "/messages?page=-1").header(HttpHeaders.AUTHORIZATION, bearer(agent)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void requiresAuthentication() throws Exception {
            mvc.perform(get(base + "/messages")).andExpect(status().isUnauthorized());
        }

        @Test
        void aNonMemberIsNotFound() throws Exception {
            when(history.list(any(), any(), any(), eq(0), eq(20))).thenThrow(new NotAMemberException());

            mvc.perform(get(base + "/messages").header(HttpHeaders.AUTHORIZATION, bearer(agent)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void anotherTenantsConversationIsNotFound() throws Exception {
            when(history.list(any(), any(), any(), eq(0), eq(20))).thenThrow(new ConversationNotFoundException());

            mvc.perform(get(base + "/messages").header(HttpHeaders.AUTHORIZATION, bearer(agent)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("Conversation not found"));
        }
    }

    @Nested
    class UpdateStatus {

        private MockHttpServletRequestBuilder update(User user, String body) {
            return patch(base).header(HttpHeaders.AUTHORIZATION, bearer(user))
                    .contentType(MediaType.APPLICATION_JSON).content(body);
        }

        @Test
        void anAgentClosesTheConversation() throws Exception {
            when(conversations.updateStatus(agent.getId().toString(), orgId, conversationId,
                    ConversationStatus.CLOSED)).thenReturn(detail(ConversationStatus.CLOSED));

            mvc.perform(update(agent, "{\"status\":\"CLOSED\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CLOSED"));
            verify(conversations).updateStatus(agent.getId().toString(), orgId, conversationId,
                    ConversationStatus.CLOSED);
        }

        @Test
        void anAdminMayUpdateToo() throws Exception {
            when(conversations.updateStatus(any(), any(), any(), eq(ConversationStatus.OPEN)))
                    .thenReturn(detail(ConversationStatus.OPEN));

            mvc.perform(update(admin, "{\"status\":\"OPEN\"}")).andExpect(status().isOk());
        }

        @Test
        void requiresAuthentication() throws Exception {
            mvc.perform(patch(base).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"OPEN\"}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void aMissingStatusIsBadRequest() throws Exception {
            mvc.perform(update(agent, "{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.status").exists());
        }

        @Test
        void anUnknownStatusIsBadRequest() throws Exception {
            mvc.perform(update(agent, "{\"status\":\"ARCHIVED\"}")).andExpect(status().isBadRequest());
        }

        @Test
        void aNonMemberIsNotFound() throws Exception {
            when(conversations.updateStatus(any(), any(), any(), any())).thenThrow(new NotAMemberException());

            mvc.perform(update(agent, "{\"status\":\"CLOSED\"}")).andExpect(status().isNotFound());
        }

        @Test
        void anotherTenantsConversationIsNotFound() throws Exception {
            when(conversations.updateStatus(any(), any(), any(), any()))
                    .thenThrow(new ConversationNotFoundException());

            mvc.perform(update(agent, "{\"status\":\"CLOSED\"}"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("Conversation not found"));
        }
    }

    @Nested
    class ReadState {

        private MockHttpServletRequestBuilder mark(User user, String body) {
            return put(base + "/read-state").header(HttpHeaders.AUTHORIZATION, bearer(user))
                    .contentType(MediaType.APPLICATION_JSON).content(body);
        }

        @Test
        void anAgentMarksTheConversationRead() throws Exception {
            mvc.perform(mark(agent, "{\"unread\":false}")).andExpect(status().isNoContent());
            verify(conversations).updateReadState(agent.getId().toString(), orgId, conversationId, false);
        }

        @Test
        void anAdminMarksItUnread() throws Exception {
            mvc.perform(mark(admin, "{\"unread\":true}")).andExpect(status().isNoContent());
            verify(conversations).updateReadState(admin.getId().toString(), orgId, conversationId, true);
        }

        @Test
        void requiresAuthentication() throws Exception {
            mvc.perform(put(base + "/read-state").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"unread\":false}")).andExpect(status().isUnauthorized());
        }

        @Test
        void aMissingFlagIsBadRequest() throws Exception {
            mvc.perform(mark(agent, "{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.unread").exists());
        }

        @Test
        void aNonMemberIsNotFound() throws Exception {
            doThrow(new NotAMemberException()).when(conversations).updateReadState(any(), any(), any(),
                    anyBoolean());

            mvc.perform(mark(agent, "{\"unread\":false}")).andExpect(status().isNotFound());
        }

        @Test
        void anotherTenantsConversationIsNotFound() throws Exception {
            doThrow(new ConversationNotFoundException()).when(conversations).updateReadState(any(), any(), any(),
                    anyBoolean());

            mvc.perform(mark(agent, "{\"unread\":false}"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("Conversation not found"));
        }
    }
}
