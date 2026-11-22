package com.connecta.backend.meta.controller;

import com.connecta.backend.meta.dto.MetaAuthorizationStatusResponse;
import com.connecta.backend.meta.service.MetaAuthorizationService;
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
 * The browser is sent here by Meta, so it carries no access token. It is public in SecurityConfig and protected
 * by the signed, expiring state instead.
 */
@RestController
@Validated
@Tag(name = "Meta connection", description = "Meta's redirect after the admin approves the connection")
@RequestMapping(MetaAuthorizationService.CALLBACK_PATH)
@RequiredArgsConstructor
class MetaOAuthCallbackController {

    private final MetaAuthorizationService authorizations;

    @Operation(summary = "Completes the Meta connection from Meta's redirect (public, state-protected)")
    @ApiResponse(responseCode = "200", description = "The organization is connected")
    @ApiResponse(responseCode = "400", description = "Missing code or state, or an invalid or expired state")
    @ApiResponse(responseCode = "403", description = "The admin who started the connection no longer holds ADMIN")
    @ApiResponse(responseCode = "502", description = "Meta refused the token exchange")
    @ApiResponse(responseCode = "503", description = "Meta is not configured on this server")
    @GetMapping
    MetaAuthorizationStatusResponse callback(@RequestParam @NotBlank String code,
            @RequestParam @NotBlank String state) {
        return MetaAuthorizationStatusResponse.from(authorizations.completeAuthorization(code, state));
    }
}
