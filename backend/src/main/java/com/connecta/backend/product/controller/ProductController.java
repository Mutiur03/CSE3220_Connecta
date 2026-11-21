package com.connecta.backend.product.controller;

import com.connecta.backend.common.dto.PageResponse;
import com.connecta.backend.product.domain.StockStatus;
import com.connecta.backend.product.dto.CreateProductRequest;
import com.connecta.backend.product.dto.ProductResponse;
import com.connecta.backend.product.dto.UpdateProductRequest;
import com.connecta.backend.product.service.ProductFilter;
import com.connecta.backend.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Role rules (checked by ProductService through TenantAccessService). Reads need any membership of the
 * organization. Create, update and delete need ADMIN of it. A non-member or another tenant's id is 404.
 */
@RestController
@Tag(name = "Products", description = "The product catalog of an organization")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService products;

    @Operation(summary = "List the organization's products by name; filter with q (name or SKU), categoryId, stockStatus (any member)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "One page of products")
    @ApiResponse(responseCode = "400", description = "Page or size out of range, or unknown stockStatus")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
    @ApiResponse(responseCode = "404", description = "Not found, unknown categoryId, or the caller is not a member")
    @GetMapping("/api/organizations/{organizationId}/products")
    PageResponse<ProductResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID organizationId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) StockStatus stockStatus,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "" + PageResponse.DEFAULT_SIZE) @Min(1) @Max(PageResponse.MAX_SIZE) int size) {
        return products.list(jwt.getSubject(), organizationId, new ProductFilter(q, categoryId, stockStatus),
                page, size);
    }

    @Operation(summary = "Create a product (ADMIN of it)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "201", description = "Product created; Location points to it")
    @ApiResponse(responseCode = "400", description = "Validation failed (name required, length limits, negative price or reorder level)")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
    @ApiResponse(responseCode = "403", description = "Caller is an AGENT of the organization")
    @ApiResponse(responseCode = "404", description = "Organization or category not found, or caller not a member")
    @ApiResponse(responseCode = "409", description = "A product with this name or SKU already exists")
    @PostMapping("/api/organizations/{organizationId}/products")
    ResponseEntity<ProductResponse> create(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID organizationId,
            @Valid @RequestBody CreateProductRequest request) {
        ProductResponse created = products.create(jwt.getSubject(), organizationId, request);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri()).body(created);
    }

    @Operation(summary = "Get one product (any member)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "The product")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
    @ApiResponse(responseCode = "404", description = "Not found, another organization's, or caller not a member")
    @GetMapping("/api/organizations/{organizationId}/products/{productId}")
    ProductResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID organizationId,
            @PathVariable UUID productId) {
        return products.get(jwt.getSubject(), organizationId, productId);
    }

    @Operation(summary = "Replace a product (ADMIN of it; a null categoryId clears the category)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "Product updated")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
    @ApiResponse(responseCode = "403", description = "Caller is an AGENT of the organization")
    @ApiResponse(responseCode = "404", description = "Product or category not found, or caller not a member")
    @ApiResponse(responseCode = "409", description = "Another product already has this name or SKU")
    @PutMapping("/api/organizations/{organizationId}/products/{productId}")
    ProductResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID organizationId,
            @PathVariable UUID productId, @Valid @RequestBody UpdateProductRequest request) {
        return products.update(jwt.getSubject(), organizationId, productId, request);
    }

    @Operation(summary = "Delete a product (ADMIN of it)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "204", description = "Product deleted")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
    @ApiResponse(responseCode = "403", description = "Caller is an AGENT of the organization")
    @ApiResponse(responseCode = "404", description = "Not found, another organization's, or caller not a member")
    @DeleteMapping("/api/organizations/{organizationId}/products/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID organizationId, @PathVariable UUID productId) {
        products.delete(jwt.getSubject(), organizationId, productId);
    }
}
