package com.connecta.backend.share.controller;

import com.connecta.backend.share.dto.ProductSharePreviewResponse;
import com.connecta.backend.share.service.ProductSharePreviewService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Role rule: any member of the organization (ADMIN or AGENT) may preview. The URL rule in SecurityConfig is
 * "authenticated"; the service checks the membership. A non-member, another tenant's conversation or product get 404.
 */
@RestController
@Tag(name = "Product sharing", description = "Share a catalog product into a conversation")
@RequestMapping("/api/organizations/{organizationId}/conversations/{conversationId}/product-shares")
@RequiredArgsConstructor
public class ProductSharePreviewController {

    private final ProductSharePreviewService previews;

    @Operation(summary = "Preview the message that sharing a product would send (any member)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "The composed text and the first image, if any")
    @ApiResponse(responseCode = "400", description = "Missing or malformed productId")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
    @ApiResponse(responseCode = "404", description = "Not found, another organization's, or not a member")
    @GetMapping("/preview")
    ProductSharePreviewResponse preview(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID organizationId,
            @PathVariable UUID conversationId, @RequestParam UUID productId) {
        return previews.preview(jwt.getSubject(), organizationId, conversationId, productId);
    }
}
