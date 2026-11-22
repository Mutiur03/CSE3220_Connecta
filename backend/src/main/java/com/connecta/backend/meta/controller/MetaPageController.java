package com.connecta.backend.meta.controller;

import com.connecta.backend.common.dto.PageResponse;
import com.connecta.backend.meta.dto.ConnectPagesRequest;
import com.connecta.backend.meta.dto.MetaPageResponse;
import com.connecta.backend.meta.service.MetaPageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Role rules: listing and connecting Pages need ADMIN of the organization. The URL rule in SecurityConfig requires
 * the ADMIN role and the service checks the organization role. A non-member gets 404, an AGENT gets 403, a Page
 * outside the Meta grant gets 422.
 */
@RestController
@Tag(name = "Meta connection", description = "Facebook Pages connected to an organization for Messenger")
@RequestMapping("/api/organizations/{organizationId}/meta/pages")
@RequiredArgsConstructor
class MetaPageController {

    private final MetaPageService pages;

    @Operation(summary = "List the organization's connected Facebook Pages, by name (ADMIN of the organization)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "One page of connected Pages; no tokens")
    @ApiResponse(responseCode = "400", description = "Page or size out of range")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
    @ApiResponse(responseCode = "403", description = "The caller is not an ADMIN of this organization")
    @ApiResponse(responseCode = "404", description = "Not found, or the caller is not a member")
    @GetMapping
    PageResponse<MetaPageResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID organizationId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "" + PageResponse.DEFAULT_SIZE) @Min(1) @Max(PageResponse.MAX_SIZE) int size) {
        return PageResponse.from(pages.connectedPages(jwt.getSubject(), organizationId, page, size),
                MetaPageResponse::from);
    }

    @Operation(summary = "Connect Facebook Pages from the Meta grant (ADMIN of the organization)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "The connected Pages; reconnecting replaces their tokens")
    @ApiResponse(responseCode = "400", description = "Missing or invalid page ids")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
    @ApiResponse(responseCode = "403", description = "The caller is not an ADMIN of this organization")
    @ApiResponse(responseCode = "404", description = "Not found, or the caller is not a member")
    @ApiResponse(responseCode = "422", description = "A page is not part of the Meta grant, or Meta is not connected")
    @ApiResponse(responseCode = "502", description = "Meta refused the page lookup or the subscription")
    @ApiResponse(responseCode = "503", description = "Meta is not configured on this server")
    @PostMapping
    List<MetaPageResponse> connect(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID organizationId,
            @Valid @RequestBody ConnectPagesRequest request) {
        return pages.connect(jwt.getSubject(), organizationId, request.pageIds()).stream()
                .map(MetaPageResponse::from)
                .toList();
    }
}
