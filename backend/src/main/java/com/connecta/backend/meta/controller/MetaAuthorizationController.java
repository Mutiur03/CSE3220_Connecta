package com.connecta.backend.meta.controller;

import com.connecta.backend.meta.dto.MetaAuthorizationStartResponse;
import com.connecta.backend.meta.dto.MetaAuthorizationStatusResponse;
import com.connecta.backend.meta.service.MetaAuthorizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Role rules: starting and reading the Meta connection need ADMIN of the organization. The URL rule in
 * SecurityConfig requires the ADMIN role and the service checks the organization role. A non-member gets 404,
 * an AGENT gets 403.
 */
@RestController
@Tag(name = "Meta connection", description = "Connects an organization's Facebook Page and Instagram account")
@RequestMapping("/api/organizations/{organizationId}/meta/authorization")
@RequiredArgsConstructor
class MetaAuthorizationController {

    private final MetaAuthorizationService authorizations;

    @Operation(summary = "Start connecting Meta: returns the Meta login URL (ADMIN of the organization)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "The login URL to open in the browser")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
    @ApiResponse(responseCode = "403", description = "The caller is not an ADMIN of this organization")
    @ApiResponse(responseCode = "404", description = "Not found, or the caller is not a member")
    @ApiResponse(responseCode = "503", description = "Meta is not configured on this server")
    @PostMapping
    MetaAuthorizationStartResponse start(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID organizationId) {
        return new MetaAuthorizationStartResponse(authorizations.startAuthorization(jwt.getSubject(), organizationId));
    }

    @Operation(summary = "Whether the organization is connected to Meta (ADMIN of the organization)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "The connection status; no token is returned")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
    @ApiResponse(responseCode = "403", description = "The caller is not an ADMIN of this organization")
    @ApiResponse(responseCode = "404", description = "Not found, or the caller is not a member")
    @GetMapping
    MetaAuthorizationStatusResponse status(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID organizationId) {
        return authorizations.authorization(jwt.getSubject(), organizationId)
                .map(MetaAuthorizationStatusResponse::from)
                .orElseGet(MetaAuthorizationStatusResponse::notConnected);
    }
}
