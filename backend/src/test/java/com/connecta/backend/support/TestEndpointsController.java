package com.connecta.backend.support;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints that exist only in tests, to exercise security rules without touching production code. */
@RestController
public class TestEndpointsController {

    @GetMapping("/api/test/protected")
    public String protectedEndpoint() {
        return "ok";
    }
}
