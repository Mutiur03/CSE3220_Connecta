package com.connecta.backend.realtime.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.connecta.backend.config.JwtProperties;
import com.connecta.backend.org.service.NotAMemberException;
import com.connecta.backend.realtime.dto.TicketResponse;
import com.connecta.backend.realtime.service.RealtimeTicketService;
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

/** The ticket endpoint: any member (ADMIN or AGENT) gets 201; no token is 401; a non-member is 404. */
@SpringBootTest
@AutoConfigureMockMvc
@MockRepositories
class RealtimeTicketControllerIntegrationTests {

    private final UUID orgId = UUID.randomUUID();
    private final String url = "/api/organizations/" + orgId + "/realtime/tickets";
    private final User agent = TestUsers.user("agent@example.com", Roles.AGENT);
    private final User admin = TestUsers.user("admin@example.com", Roles.ADMIN);

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtProperties props;

    @MockitoBean
    RealtimeTicketService tickets;

    @Test
    void anAgentGetsATicket() throws Exception {
        when(tickets.issue(agent.getId().toString(), orgId)).thenReturn(new TicketResponse("abc", 30));

        mvc.perform(post(url).header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(props, agent)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ticket").value("abc"))
                .andExpect(jsonPath("$.expiresInSeconds").value(30));
    }

    @Test
    void anAdminGetsATicket() throws Exception {
        when(tickets.issue(admin.getId().toString(), orgId)).thenReturn(new TicketResponse("xyz", 30));

        mvc.perform(post(url).header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(props, admin)))
                .andExpect(status().isCreated());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(post(url)).andExpect(status().isUnauthorized());
    }

    @Test
    void aNonMemberOrAnotherTenantIsNotFound() throws Exception {
        when(tickets.issue(any(), any())).thenThrow(new NotAMemberException());

        mvc.perform(post(url).header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(props, agent)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
