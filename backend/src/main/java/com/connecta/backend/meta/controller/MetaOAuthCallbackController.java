package com.connecta.backend.meta.controller;

import com.connecta.backend.config.FrontendProperties;
import com.connecta.backend.meta.domain.MetaAuthorization;
import com.connecta.backend.meta.service.MetaAuthorizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The browser is sent here by Meta, so it carries no access token. It is public in SecurityConfig and protected
 * by the signed, expiring state. On success or failure the browser is sent back to the Integrations page.
 */
@RestController
@Tag(name = "Meta connection", description = "Meta's redirect after the admin approves the connection")
@RequestMapping(MetaAuthorizationService.CALLBACK_PATH)
@RequiredArgsConstructor
class MetaOAuthCallbackController {

    private final MetaAuthorizationService authorizations;
    private final FrontendProperties frontend;

    @Operation(summary = "Completes the Meta connection from Meta's redirect, then redirects to the web app (public, state-protected)")
    @ApiResponse(responseCode = "302", description = "Connected: back to the Integrations page. Failed: back with metaError")
    @GetMapping
    ResponseEntity<Void> callback(@RequestParam(required = false) String code,
            @RequestParam(required = false) String state) {
        MetaAuthorization grant = authorizations.completeAuthorization(code, state);
        return MetaCallbackRedirects.to(MetaCallbackRedirects.integrations(frontend.baseUrl(),
                grant.getOrganization().getId(), "facebook=connected"));
    }
}
