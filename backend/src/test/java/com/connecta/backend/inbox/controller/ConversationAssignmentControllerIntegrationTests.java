package com.connecta.backend.inbox.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.connecta.backend.common.dto.PageResponse;
import com.connecta.backend.config.JwtProperties;
import com.connecta.backend.inbox.domain.AssignmentSource;
import com.connecta.backend.inbox.dto.ConversationAssigneeResponse;
import com.connecta.backend.inbox.dto.ConversationAssignmentResponse;
import com.connecta.backend.inbox.service.AssigneeNotAvailableException;
import com.connecta.backend.inbox.service.AssignmentUnchangedException;
import com.connecta.backend.inbox.service.ConversationAssignmentService;
import com.connecta.backend.inbox.service.ConversationNotFoundException;
import com.connecta.backend.org.service.InsufficientOrganizationRoleException;
import com.connecta.backend.org.service.MemberNotFoundException;
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
 * Manual assignment endpoints with the service mocked: ADMIN allowed, AGENT denied by the URL rule before the
 * service runs, 401 without a token, and the problem detail each failure maps to.
 */
@SpringBootTest
@AutoConfigureMockMvc
@MockRepositories
class ConversationAssignmentControllerIntegrationTests {

    private final UUID orgId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();
    private final UUID membershipId = UUID.randomUUID();
    private final String base = "/api/organizations/" + orgId + "/conversations/" + conversationId;

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtProperties props;

    @MockitoBean
    ConversationAssignmentService assignments;

    private final User admin = TestUsers.user("admin@example.com", Roles.ADMIN);
    private final User agent = TestUsers.user("agent@example.com", Roles.AGENT);

    private MockHttpServletRequestBuilder assign(User user, String json) {
        return post(base + "/assignment").header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(props, user))
                .contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private String body() {
        return "{\"membershipId\":\"" + membershipId + "\"}";
    }

    @Nested
    class Assign {

        @Test
        void anAdminAssignsAndGetsTheAssignee() throws Exception {
            UUID userId = UUID.randomUUID();
            when(assignments.assign(admin.getId().toString(), orgId, conversationId, membershipId))
                    .thenReturn(new ConversationAssigneeResponse(conversationId, membershipId, userId, "Rina"));

            mvc.perform(assign(admin, body()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.conversationId").value(conversationId.toString()))
                    .andExpect(jsonPath("$.membershipId").value(membershipId.toString()))
                    .andExpect(jsonPath("$.userId").value(userId.toString()))
                    .andExpect(jsonPath("$.name").value("Rina"));
        }

        @Test
        void aNullMembershipUnassigns() throws Exception {
            when(assignments.assign(admin.getId().toString(), orgId, conversationId, null))
                    .thenReturn(new ConversationAssigneeResponse(conversationId, null, null, null));

            mvc.perform(assign(admin, "{\"membershipId\":null}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.membershipId").doesNotExist());
        }

        @Test
        void aMalformedMembershipIdIsBadRequest() throws Exception {
            mvc.perform(assign(admin, "{\"membershipId\":\"not-a-uuid\"}")).andExpect(status().isBadRequest());
        }

        @Test
        void requiresAuthentication() throws Exception {
            mvc.perform(post(base + "/assignment").contentType(MediaType.APPLICATION_JSON).content(body()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void anAgentIsForbiddenAndTheServiceIsNotCalled() throws Exception {
            mvc.perform(assign(agent, body())).andExpect(status().isForbidden());

            verifyNoInteractions(assignments);
        }

        @Test
        void aMemberWithTooLowAnOrganizationRoleIsForbidden() throws Exception {
            when(assignments.assign(any(), any(), any(), any())).thenThrow(new InsufficientOrganizationRoleException());

            mvc.perform(assign(admin, body())).andExpect(status().isForbidden());
        }

        @Test
        void anotherTenantsConversationIsNotFound() throws Exception {
            when(assignments.assign(any(), any(), any(), any())).thenThrow(new ConversationNotFoundException());

            mvc.perform(assign(admin, body()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("Conversation not found"));
        }

        @Test
        void anotherTenantsMemberIsNotFound() throws Exception {
            when(assignments.assign(any(), any(), any(), any())).thenThrow(new MemberNotFoundException());

            mvc.perform(assign(admin, body()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("Member not found"));
        }

        @Test
        void assigningTheCurrentAssigneeIsUnprocessable() throws Exception {
            when(assignments.assign(any(), any(), any(), any())).thenThrow(new AssignmentUnchangedException());

            mvc.perform(assign(admin, body()))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.status").value(422));
        }

        @Test
        void aDisabledMemberIsUnprocessable() throws Exception {
            when(assignments.assign(any(), any(), any(), any())).thenThrow(new AssigneeNotAvailableException());

            mvc.perform(assign(admin, body())).andExpect(status().isUnprocessableContent());
        }
    }

    @Nested
    class History {

        @Test
        void anAdminGetsOnePageWithTheDefaultPaging() throws Exception {
            UUID rowId = UUID.randomUUID();
            ConversationAssignmentResponse row = new ConversationAssignmentResponse(rowId, null,
                    new ConversationAssignmentResponse.Person(membershipId, UUID.randomUUID(), "Rina"),
                    new ConversationAssignmentResponse.Person(null, admin.getId(), "Boss"),
                    AssignmentSource.MANUAL, Instant.parse("2026-11-26T10:00:00Z"));
            when(assignments.list(admin.getId().toString(), orgId, conversationId, 0, 20))
                    .thenReturn(new PageResponse<>(List.of(row), 0, 20, 1));

            mvc.perform(get(base + "/assignments").header(HttpHeaders.AUTHORIZATION,
                            TestTokens.bearer(props, admin)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(rowId.toString()))
                    .andExpect(jsonPath("$.content[0].source").value("MANUAL"))
                    .andExpect(jsonPath("$.content[0].to.name").value("Rina"))
                    .andExpect(jsonPath("$.content[0].assignedBy.name").value("Boss"))
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.size").value(20))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        void aSizeOverTheMaximumIsBadRequest() throws Exception {
            mvc.perform(get(base + "/assignments?size=101").header(HttpHeaders.AUTHORIZATION,
                    TestTokens.bearer(props, admin))).andExpect(status().isBadRequest());
        }

        @Test
        void anAgentIsForbidden() throws Exception {
            mvc.perform(get(base + "/assignments").header(HttpHeaders.AUTHORIZATION,
                    TestTokens.bearer(props, agent))).andExpect(status().isForbidden());
        }

        @Test
        void requiresAuthentication() throws Exception {
            mvc.perform(get(base + "/assignments")).andExpect(status().isUnauthorized());
        }

        @Test
        void anotherTenantsConversationIsNotFound() throws Exception {
            when(assignments.list(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt(),
                    org.mockito.ArgumentMatchers.anyInt())).thenThrow(new ConversationNotFoundException());

            mvc.perform(get(base + "/assignments").header(HttpHeaders.AUTHORIZATION,
                    TestTokens.bearer(props, admin))).andExpect(status().isNotFound());
        }
    }
}
