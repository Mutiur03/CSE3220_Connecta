package com.connecta.backend.meta.controller;

import com.connecta.backend.meta.dto.MetaConnectedAccountResponse;
import com.connecta.backend.meta.service.InstagramConnectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Instagram redirects the browser here without an access token. It is public in SecurityConfig and protected by
 * the signed, expiring state instead.
 */
@RestController
@Validated
@Tag(name = "Instagram connection", description = "Instagram's redirect after the admin approves the connection")
@RequestMapping(InstagramConnectService.CALLBACK_PATH)
@RequiredArgsConstructor
class InstagramOAuthCallbackController {

    private final InstagramConnectService connections;

    @Operation(summary = "Completes the Instagram connection from Instagram's redirect (public, state-protected)")
    @ApiResponse(responseCode = "200", description = "The Instagram account is connected")
    @ApiResponse(responseCode = "400", description = "Missing code or state, or an invalid or expired state")
    @ApiResponse(responseCode = "403", description = "The admin who started the connection no longer holds ADMIN")
    @ApiResponse(responseCode = "502", description = "Instagram refused the token exchange")
    @ApiResponse(responseCode = "503", description = "Instagram is not configured on this server")
    @GetMapping
    MetaConnectedAccountResponse callback(@RequestParam @NotBlank String code, @RequestParam @NotBlank String state) {
        return MetaConnectedAccountResponse.from(connections.completeConnection(code, state));
    }
}
