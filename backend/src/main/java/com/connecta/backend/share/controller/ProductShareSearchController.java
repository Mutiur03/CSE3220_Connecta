package com.connecta.backend.share.controller;

import com.connecta.backend.common.dto.PageResponse;
import com.connecta.backend.share.dto.ProductShareCandidateResponse;
import com.connecta.backend.share.service.ProductShareSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Role rule: any member of the organization (ADMIN or AGENT) may search. The URL rule in SecurityConfig is
 * "authenticated"; the service checks the membership. A non-member and another tenant's conversation get 404.
 */
@RestController
@Tag(name = "Product sharing", description = "Share catalog products in a conversation")
@RequestMapping("/api/organizations/{organizationId}/conversations/{conversationId}")
@RequiredArgsConstructor
public class ProductShareSearchController {

    private final ProductShareSearchService search;

    @Operation(summary = "Search the organization's products by name or SKU to share in a conversation (any member)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "One page of candidates with their first image URL or null")
    @ApiResponse(responseCode = "400", description = "Page or size out of range")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
    @ApiResponse(responseCode = "404", description = "Not found, another organization's, or not a member")
    @GetMapping("/product-search")
    PageResponse<ProductShareCandidateResponse> search(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID organizationId, @PathVariable UUID conversationId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "" + PageResponse.DEFAULT_SIZE) @Min(1) @Max(PageResponse.MAX_SIZE) int size) {
        return search.search(jwt.getSubject(), organizationId, conversationId, q, page, size);
    }
}
