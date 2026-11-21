package com.connecta.backend.product.controller;

import com.connecta.backend.common.dto.PageResponse;
import com.connecta.backend.product.dto.ProductCategoryRequest;
import com.connecta.backend.product.dto.ProductCategoryResponse;
import com.connecta.backend.product.service.ProductCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Role rules: reads need any membership of the organization; create, update and delete need ADMIN of it.
 * The URL rules sit in SecurityConfig (writes need ADMIN) and the service checks the organization role.
 * A non-member gets 404, an AGENT writing gets 403.
 */
@RestController
@Tag(name = "Product categories", description = "Groups of products inside one organization")
@RequestMapping("/api/organizations/{organizationId}/product-categories")
@RequiredArgsConstructor
public class ProductCategoryController {

    private final ProductCategoryService categories;

    @Operation(summary = "List the organization's product categories, by name (any member)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "One page of categories")
    @ApiResponse(responseCode = "400", description = "Page or size out of range")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
    @ApiResponse(responseCode = "404", description = "Not found, or the caller is not a member")
    @GetMapping
    PageResponse<ProductCategoryResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID organizationId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "" + PageResponse.DEFAULT_SIZE) @Min(1) @Max(PageResponse.MAX_SIZE) int size) {
        return categories.list(jwt.getSubject(), organizationId, page, size);
    }

    @Operation(summary = "Create a product category (ADMIN of the organization)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "201", description = "Category created, Location header set")
    @ApiResponse(responseCode = "400", description = "Invalid input")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
    @ApiResponse(responseCode = "403", description = "The caller is an AGENT of this organization")
    @ApiResponse(responseCode = "404", description = "Not found, or the caller is not a member")
    @ApiResponse(responseCode = "409", description = "A category with this name already exists")
    @PostMapping
    ResponseEntity<ProductCategoryResponse> create(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID organizationId, @Valid @RequestBody ProductCategoryRequest request) {
        ProductCategoryResponse created = categories.create(jwt.getSubject(), organizationId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @Operation(summary = "Get one product category (any member)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "The category")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
    @ApiResponse(responseCode = "404", description = "Not found, another organization's, or not a member")
    @GetMapping("/{categoryId}")
    ProductCategoryResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID organizationId,
            @PathVariable UUID categoryId) {
        return categories.get(jwt.getSubject(), organizationId, categoryId);
    }

    @Operation(summary = "Replace a product category's name and description (ADMIN of the organization)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "The updated category")
    @ApiResponse(responseCode = "400", description = "Invalid input")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
    @ApiResponse(responseCode = "403", description = "The caller is an AGENT of this organization")
    @ApiResponse(responseCode = "404", description = "Not found, another organization's, or not a member")
    @ApiResponse(responseCode = "409", description = "A category with this name already exists")
    @PutMapping("/{categoryId}")
    ProductCategoryResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID organizationId,
            @PathVariable UUID categoryId, @Valid @RequestBody ProductCategoryRequest request) {
        return categories.update(jwt.getSubject(), organizationId, categoryId, request);
    }

    @Operation(summary = "Delete a product category; its products stay, uncategorized (ADMIN of the organization)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "204", description = "Category deleted")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
    @ApiResponse(responseCode = "403", description = "The caller is an AGENT of this organization")
    @ApiResponse(responseCode = "404", description = "Not found, another organization's, or not a member")
    @DeleteMapping("/{categoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID organizationId, @PathVariable UUID categoryId) {
        categories.delete(jwt.getSubject(), organizationId, categoryId);
    }
}
