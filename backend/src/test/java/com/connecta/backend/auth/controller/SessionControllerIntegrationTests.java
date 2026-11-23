package com.connecta.backend.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.connecta.backend.auth.domain.RefreshToken;
import com.connecta.backend.auth.service.SessionNotFoundException;
import com.connecta.backend.auth.service.SessionService;
import com.connecta.backend.config.JwtProperties;
import com.connecta.backend.support.MockRepositories;
import com.connecta.backend.support.TestClock;
import com.connecta.backend.support.TestTokens;
import com.connecta.backend.support.TestUsers;
import com.connecta.backend.user.domain.Roles;
import com.connecta.backend.user.domain.User;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@MockRepositories
class SessionControllerIntegrationTests {

    static final UUID CURRENT = UUID.randomUUID();
    static final UUID OTHER = UUID.randomUUID();

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtProperties props;

    @MockitoBean
    SessionService sessions;

    final User jane = TestUsers.user("jane@example.com", Roles.AGENT);

    String bearer() {
        return TestTokens.bearer(props, jane, CURRENT);
    }

    private RefreshToken token(UUID session, String agent) {
        return new RefreshToken(jane, "hash", TestClock.NOW.plusSeconds(60), TestClock.NOW, session, agent, "203.0.113.7", "Dhaka, Bangladesh");
    }

    @Nested
    class ListSessions {

        @Test
        void listsSessionsAndMarksTheCurrentOne() throws Exception {
            when(sessions.activeSessions(any(), any())).thenReturn(
                    new PageImpl<>(List.of(token(CURRENT, "Chrome"), token(OTHER, "Firefox"))));

            mvc.perform(get("/api/auth/sessions").header(HttpHeaders.AUTHORIZATION, bearer()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(2))
                    .andExpect(jsonPath("$.content[0].id").value(CURRENT.toString()))
                    .andExpect(jsonPath("$.content[0].current").value(true))
                    .andExpect(jsonPath("$.content[0].location").value("Dhaka, Bangladesh"))
                    .andExpect(jsonPath("$.content[1].current").value(false))
                    .andExpect(jsonPath("$.content[0].tokenHash").doesNotExist());
        }

        @Test
        void requiresAuthentication() throws Exception {
            mvc.perform(get("/api/auth/sessions")).andExpect(status().isUnauthorized());
        }

        @Test
        void rejectsAnOversizedPage() throws Exception {
            mvc.perform(get("/api/auth/sessions?size=101").header(HttpHeaders.AUTHORIZATION, bearer()))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    class Revoke {

        @Test
        void revokesOneSessionOfTheCaller() throws Exception {
            mvc.perform(delete("/api/auth/sessions/" + OTHER).header(HttpHeaders.AUTHORIZATION, bearer()))
                    .andExpect(status().isNoContent());

            verify(sessions).revoke(jane.getId(), OTHER);
        }

        @Test
        void anotherUsersSessionIs404() throws Exception {
            doThrow(new SessionNotFoundException()).when(sessions).revoke(any(), any());

            mvc.perform(delete("/api/auth/sessions/" + OTHER).header(HttpHeaders.AUTHORIZATION, bearer()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404));
        }

        @Test
        void requiresAuthentication() throws Exception {
            mvc.perform(delete("/api/auth/sessions/" + OTHER)).andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class RevokeOthers {

        @Test
        void keepsTheSessionOfTheAccessToken() throws Exception {
            mvc.perform(delete("/api/auth/sessions").header(HttpHeaders.AUTHORIZATION, bearer()))
                    .andExpect(status().isNoContent());

            verify(sessions).revokeOthers(jane.getId(), CURRENT);
        }

        @Test
        void requiresAuthentication() throws Exception {
            mvc.perform(delete("/api/auth/sessions")).andExpect(status().isUnauthorized());
        }
    }
}
