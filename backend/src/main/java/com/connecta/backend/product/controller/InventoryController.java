package com.connecta.backend.product.controller;

import com.connecta.backend.product.dto.InventorySummaryResponse;
import com.connecta.backend.product.service.InventoryService;
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
import org.springframework.web.bind.annotation.RestController;

/** Role rule (checked by InventoryService through TenantAccessService): any member. A non-member is 404. */
@RestController
@Tag(name = "Inventory", description = "Totals, stock value and low or out-of-stock counts for an organization")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventory;

    @Operation(summary = "Inventory summary: product count, total units, stock value, low and out-of-stock counts (any member)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "The summary")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
    @ApiResponse(responseCode = "404", description = "Organization not found, or caller not a member")
    @GetMapping("/api/organizations/{organizationId}/inventory/summary")
    InventorySummaryResponse summary(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID organizationId) {
        return inventory.summary(jwt.getSubject(), organizationId);
    }
}
