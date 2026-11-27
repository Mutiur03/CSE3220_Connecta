package com.connecta.backend.share.controller;

import com.connecta.backend.share.dto.ProductShareRequest;
import com.connecta.backend.share.dto.ProductShareResponse;
import com.connecta.backend.share.service.ProductShareService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Role rule: any member of the organization (ADMIN or AGENT) may share. The URL rule in SecurityConfig is
 * "authenticated"; the service checks the membership. A non-member, another tenant's conversation or product get 404.
 */
@RestController
@Tag(name = "Product sharing", description = "Share a catalog product into a conversation")
@RequestMapping("/api/organizations/{organizationId}/conversations/{conversationId}/product-shares")
@RequiredArgsConstructor
public class ProductShareController {

    private final ProductShareService shares;

    @Operation(summary = "Send a product's image and text to the customer (any member)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "201", description = "Sent and stored: the image message when it went out, then the text")
    @ApiResponse(responseCode = "400", description = "Missing productId, blank or too long text")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
    @ApiResponse(responseCode = "404", description = "Not found, another organization's, or not a member")
    @ApiResponse(responseCode = "422", description = "Channel not connected, or Meta's reply window is closed")
    @ApiResponse(responseCode = "502", description = "Meta could not deliver the text")
    @PostMapping
    ResponseEntity<ProductShareResponse> share(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID organizationId,
            @PathVariable UUID conversationId, @Valid @RequestBody ProductShareRequest request) {
        ProductShareResponse sent = shares.share(jwt.getSubject(), organizationId, conversationId,
                request.productId(), request.text());
        URI location = URI.create("/api/organizations/" + organizationId + "/conversations/" + conversationId
                + "/messages");
        return ResponseEntity.created(location).body(sent);
    }
}
