package com.connecta.backend.meta.controller;

import com.connecta.backend.config.FrontendProperties;
import com.connecta.backend.meta.domain.MetaConnectedAccount;
import com.connecta.backend.meta.service.InstagramConnectService;
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
 * Instagram redirects the browser here without an access token. It is public in SecurityConfig and protected by
 * the signed, expiring state. On success or failure the browser is sent back to the Integrations page.
 */
@RestController
@Tag(name = "Instagram connection", description = "Instagram's redirect after the admin approves the connection")
@RequestMapping(InstagramConnectService.CALLBACK_PATH)
@RequiredArgsConstructor
class InstagramOAuthCallbackController {

    private final InstagramConnectService connections;
    private final FrontendProperties frontend;

    @Operation(summary = "Completes the Instagram connection from Instagram's redirect, then redirects to the web app (public, state-protected)")
    @ApiResponse(responseCode = "302", description = "Connected: back to the Integrations page. Failed: back with metaError")
    @GetMapping
    ResponseEntity<Void> callback(@RequestParam(required = false) String code,
            @RequestParam(required = false) String state) {
        MetaConnectedAccount account = connections.completeConnection(code, state);
        return MetaCallbackRedirects.to(MetaCallbackRedirects.integrations(frontend.baseUrl(),
                account.getOrganization().getId(), "instagram=connected"));
    }
}
