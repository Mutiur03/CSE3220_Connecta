package com.connecta.backend.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.connecta.backend.user.domain.Roles;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

class MetricsScrapeAccessTests {

    private static final String TOKEN = "s3cret-scrape-token";
    private final RoleHierarchy hierarchy = RoleHierarchyImpl.fromHierarchy(
            "ROLE_OWNER > ROLE_ADMIN\nROLE_ADMIN > ROLE_AGENT");

    private boolean allowed(String configured, String presented, Authentication auth) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/prometheus");
        if (presented != null) {
            request.addHeader(MetricsScrapeAccess.HEADER, presented);
        }
        return new MetricsScrapeAccess(configured, hierarchy)
                .authorize(() -> auth, new RequestAuthorizationContext(request)).isGranted();
    }

    private static Authentication role(String role) {
        return new TestingAuthenticationToken("u", "p", Roles.AUTHORITY_PREFIX + role);
    }

    @Nested
    class Token {
        @Test
        void matchingTokenIsAllowedWithoutLogin() {
            assertTrue(allowed(TOKEN, TOKEN, null));
        }

        @Test
        void wrongOrMissingTokenIsDenied() {
            assertFalse(allowed(TOKEN, "other", null));
            assertFalse(allowed(TOKEN, null, null));
        }

        @Test
        void blankConfiguredTokenNeverMatches() {
            assertFalse(allowed("", "", null));
            assertFalse(allowed(null, "x", null));
        }
    }

    @Nested
    class Roles_ {
        @Test
        void adminAndOwnerAreAllowed() {
            assertTrue(allowed(TOKEN, null, role(Roles.ADMIN)));
            assertTrue(allowed(TOKEN, null, role(Roles.OWNER)));
        }

        @Test
        void agentIsDenied() {
            assertFalse(allowed(TOKEN, null, role(Roles.AGENT)));
        }
    }
}
