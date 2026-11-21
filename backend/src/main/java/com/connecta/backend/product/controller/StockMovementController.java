package com.connecta.backend.product.controller;

import com.connecta.backend.common.dto.PageResponse;
import com.connecta.backend.product.dto.RecordStockMovementRequest;
import com.connecta.backend.product.dto.StockMovementResponse;
import com.connecta.backend.product.service.StockMovementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Role rules (checked by StockMovementService through TenantAccessService): any member of the organization
 * may record and read stock movements. A non-member or another tenant's id is 404.
 */
@RestController
@Tag(name = "Stock movements", description = "Receive, issue and adjust product stock, with its history")
@RequiredArgsConstructor
public class StockMovementController {

    private final StockMovementService stockMovements;

    @Operation(summary = "Record a stock movement: RECEIVE or ISSUE a quantity, or ADJUST to a level (any member)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "201", description = "Movement recorded; Location points to it")
    @ApiResponse(responseCode = "400", description = "Validation failed, or the quantity is below the minimum for its type")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
    @ApiResponse(responseCode = "404", description = "Product not found, or caller not a member")
    @ApiResponse(responseCode = "422", description = "An ISSUE asks for more than the stock on hand")
    @PostMapping("/api/organizations/{organizationId}/products/{productId}/stock-movements")
    ResponseEntity<StockMovementResponse> record(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID organizationId,
            @PathVariable UUID productId, @Valid @RequestBody RecordStockMovementRequest request) {
        StockMovementResponse created = stockMovements.record(jwt.getSubject(), organizationId, productId, request);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/organizations/{organizationId}/stock-movements/{id}")
                .buildAndExpand(organizationId, created.id()).toUri()).body(created);
    }

    @Operation(summary = "List one product's stock movements, newest first (any member)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "One page of movements")
    @ApiResponse(responseCode = "400", description = "Page or size out of range")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
    @ApiResponse(responseCode = "404", description = "Product not found, or caller not a member")
    @GetMapping("/api/organizations/{organizationId}/products/{productId}/stock-movements")
    PageResponse<StockMovementResponse> listForProduct(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID organizationId, @PathVariable UUID productId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "" + PageResponse.DEFAULT_SIZE) @Min(1) @Max(PageResponse.MAX_SIZE) int size) {
        return stockMovements.listForProduct(jwt.getSubject(), organizationId, productId, page, size);
    }

    @Operation(summary = "List the organization's stock movements, newest first; productId narrows to one product (any member)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "One page of movements")
    @ApiResponse(responseCode = "400", description = "Page or size out of range, or productId is not a UUID")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
    @ApiResponse(responseCode = "404", description = "productId not in this organization, or caller not a member")
    @GetMapping("/api/organizations/{organizationId}/stock-movements")
    PageResponse<StockMovementResponse> listForOrganization(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID organizationId,
            @RequestParam(required = false) UUID productId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "" + PageResponse.DEFAULT_SIZE) @Min(1) @Max(PageResponse.MAX_SIZE) int size) {
        return stockMovements.listForOrganization(jwt.getSubject(), organizationId, productId, page, size);
    }

    @Operation(summary = "Get one stock movement (any member)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "The movement")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
    @ApiResponse(responseCode = "404", description = "Not found, another organization's, or caller not a member")
    @GetMapping("/api/organizations/{organizationId}/stock-movements/{movementId}")
    StockMovementResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID organizationId,
            @PathVariable UUID movementId) {
        return stockMovements.get(jwt.getSubject(), organizationId, movementId);
    }
}
