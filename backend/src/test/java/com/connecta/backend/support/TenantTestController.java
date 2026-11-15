package com.connecta.backend.support;

import com.connecta.backend.org.service.TenantAccessService;
import com.connecta.backend.user.domain.Roles;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Test-only endpoints that run tenant checks for the caller. Never in production code. */
@RestController
public class TenantTestController {

    private final TenantAccessService tenants;

    public TenantTestController(TenantAccessService tenants) {
        this.tenants = tenants;
    }

    @GetMapping("/api/test/organizations/{organizationId}/member")
    public String member(@PathVariable UUID organizationId, @AuthenticationPrincipal Jwt jwt) {
        tenants.requireMembership(organizationId, UUID.fromString(jwt.getSubject()));
        return "member";
    }

    @GetMapping("/api/test/organizations/{organizationId}/admin")
    public String admin(@PathVariable UUID organizationId, @AuthenticationPrincipal Jwt jwt) {
        tenants.requireRole(organizationId, UUID.fromString(jwt.getSubject()), Roles.ADMIN);
        return "admin";
    }
}
