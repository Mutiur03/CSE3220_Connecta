package com.connecta.backend.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.connecta.backend.auth.service.SessionService;
import com.connecta.backend.auth.service.TokenService;
import jakarta.servlet.FilterChain;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class SessionActivityFilterTests {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID SESSION = UUID.randomUUID();

    @Mock
    SessionService sessions;

    @Mock
    FilterChain chain;

    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static void signInWith(String sessionClaim) {
        Jwt.Builder builder = Jwt.withTokenValue("t").header("alg", "none").subject(USER.toString())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
        if (sessionClaim != null) {
            builder.claim(TokenService.CLAIM_SESSION, sessionClaim);
        }
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(builder.build()));
    }

    @Test
    void touchesTheSessionOfAnAuthenticatedRequest() throws Exception {
        signInWith(SESSION.toString());

        new SessionActivityFilter(sessions).doFilter(request, response, chain);

        verify(sessions).touch(USER, SESSION);
        verify(chain).doFilter(request, response);
    }

    @Test
    void ignoresAnAnonymousRequest() throws Exception {
        new SessionActivityFilter(sessions).doFilter(request, response, chain);

        verifyNoInteractions(sessions);
        verify(chain).doFilter(request, response);
    }

    @Test
    void ignoresATokenWithoutASession() throws Exception {
        signInWith(null);

        new SessionActivityFilter(sessions).doFilter(request, response, chain);

        verify(sessions, never()).touch(any(), any());
        verify(chain).doFilter(request, response);
    }

    @Test
    void aFailedWriteDoesNotFailTheRequest() throws Exception {
        signInWith(SESSION.toString());
        doThrow(new DataAccessResourceFailureException("down")).when(sessions).touch(USER, SESSION);

        new SessionActivityFilter(sessions).doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }
}
