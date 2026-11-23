package com.connecta.backend.auth.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientInfoTests {

    @Test
    void readsUserAgentAndRemoteAddress() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", "Mozilla/5.0");
        request.setRemoteAddr("203.0.113.7");

        ClientInfo info = ClientInfo.from(request);

        assertEquals("Mozilla/5.0", info.userAgent());
        assertEquals("203.0.113.7", info.ip());
    }

    @Test
    void cutsAnOversizedUserAgentToTheColumnLimit() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", "x".repeat(ClientInfo.MAX_USER_AGENT + 50));

        assertEquals(ClientInfo.MAX_USER_AGENT, ClientInfo.from(request).userAgent().length());
    }

    @Test
    void missingUserAgentStaysNull() {
        assertNull(ClientInfo.from(new MockHttpServletRequest()).userAgent());
    }
}
