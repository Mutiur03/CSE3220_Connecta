package com.connecta.backend.config;

import com.connecta.backend.user.domain.Roles;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.function.Supplier;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/**
 * Who may read /actuator/prometheus: an ADMIN (or above), or Prometheus presenting the shared scrape token in the
 * X-Scrape-Token header. Prometheus cannot sign in, and access tokens live minutes, so it needs a static secret.
 * A blank token disables the second way. Caddy never proxies /actuator, so the token is only usable on the
 * internal Docker network.
 */
class MetricsScrapeAccess implements AuthorizationManager<RequestAuthorizationContext> {

    static final String HEADER = "X-Scrape-Token";
    private static final String ADMIN_AUTHORITY = Roles.AUTHORITY_PREFIX + Roles.ADMIN;

    private final byte[] token;
    private final RoleHierarchy roleHierarchy;

    MetricsScrapeAccess(String token, RoleHierarchy roleHierarchy) {
        this.token = token == null ? new byte[0] : token.getBytes(StandardCharsets.UTF_8);
        this.roleHierarchy = roleHierarchy;
    }

    @Override
    public AuthorizationDecision authorize(Supplier<? extends Authentication> authentication,
            RequestAuthorizationContext context) {
        return new AuthorizationDecision(presentsToken(context.getRequest()) || isAdmin(authentication.get()));
    }

    private boolean presentsToken(HttpServletRequest request) {
        String presented = request.getHeader(HEADER);
        return token.length > 0 && presented != null
                && MessageDigest.isEqual(token, presented.getBytes(StandardCharsets.UTF_8));
    }

    private boolean isAdmin(Authentication auth) {
        return auth != null && auth.isAuthenticated()
                && roleHierarchy.getReachableGrantedAuthorities(auth.getAuthorities()).stream()
                        .map(GrantedAuthority::getAuthority)
                        .anyMatch(ADMIN_AUTHORITY::equals);
    }
}
