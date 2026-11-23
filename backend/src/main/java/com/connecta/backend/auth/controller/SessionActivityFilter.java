package com.connecta.backend.auth.controller;

import com.connecta.backend.auth.service.SessionService;
import com.connecta.backend.auth.service.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Marks the caller's session as active on every authenticated request, so "last active" is not only the time of
 * the last token refresh. Runs after the bearer token is checked. Never fails the request: a lost timestamp
 * is logged and the request goes on.
 */
@Slf4j
@RequiredArgsConstructor
public class SessionActivityFilter extends OncePerRequestFilter {

    private final SessionService sessions;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        touchSession(SecurityContextHolder.getContext().getAuthentication());
        chain.doFilter(request, response);
    }

    private void touchSession(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return;
        }
        UUID sessionId = TokenService.sessionId(jwt);
        if (sessionId == null) {
            return;
        }
        try {
            sessions.touch(UUID.fromString(jwt.getSubject()), sessionId);
        } catch (DataAccessException e) {
            log.warn("Could not record session activity: {}", e.getMessage());
        }
    }
}
