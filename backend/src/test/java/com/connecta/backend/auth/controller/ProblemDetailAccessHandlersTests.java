package com.connecta.backend.auth.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

class ProblemDetailAccessHandlersTests {

    private static final String INTERNAL = "secret internal detail";

    private final ProblemDetailAccessHandlers handlers = new ProblemDetailAccessHandlers();
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @Test
    void commenceWrites401ProblemJsonWithABearerChallenge() throws Exception {
        handlers.commence(request, response, new BadCredentialsException(INTERNAL));

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentType().startsWith("application/problem+json"));
        assertNotNull(response.getHeader("WWW-Authenticate"));
        assertTrue(response.getContentAsString().contains("\"status\":401"));
        assertTrue(response.getContentAsString().contains("\"title\":\"Unauthorized\""));
    }

    @Test
    void commenceDoesNotLeakTheExceptionMessage() throws Exception {
        handlers.commence(request, response, new BadCredentialsException(INTERNAL));

        assertFalse(response.getContentAsString().contains(INTERNAL));
    }

    @Test
    void handleWrites403ProblemJsonWithoutLeakingTheExceptionMessage() throws Exception {
        handlers.handle(request, response, new AccessDeniedException(INTERNAL));

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentType().startsWith("application/problem+json"));
        assertTrue(response.getContentAsString().contains("\"status\":403"));
        assertTrue(response.getContentAsString().contains("\"title\":\"Forbidden\""));
        assertFalse(response.getContentAsString().contains(INTERNAL));
    }
}
