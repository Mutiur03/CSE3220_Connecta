package com.connecta.backend.support;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints that exist only in tests, to exercise security rules without touching production code. */
@RestController
public class TestEndpointsController {

    @GetMapping("/api/test/protected")
    public String protectedEndpoint() {
        return "ok";
    }

    /** Covered by the URL rule for /api/admin/**. */
    @GetMapping("/api/admin/test")
    public String adminByUrlRule() {
        return "admin";
    }

    /** Covered by method security, the way feature controllers will restrict single operations. */
    @GetMapping("/api/test/admin-only")
    @PreAuthorize("hasRole('ADMIN')")
    public String adminByAnnotation() {
        return "admin";
    }

    @GetMapping("/api/test/agent-and-above")
    @PreAuthorize("hasRole('AGENT')")
    public String agentAndAbove() {
        return "agent";
    }
}
