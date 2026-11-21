package com.connecta.backend.org.controller;

import com.connecta.backend.common.dto.PageResponse;
import com.connecta.backend.org.dto.CreateOrganizationRequest;
import com.connecta.backend.org.dto.OrganizationResponse;
import com.connecta.backend.org.dto.UpdateOrganizationRequest;
import com.connecta.backend.org.service.OrganizationService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Role rules (the caller's role inside each organization is checked by OrganizationService):
 * list, create and get need a signed-in user, and get is limited to members; rename and delete need
 * ADMIN of that organization. An AGENT gets 403, and a non-member gets 404.
 */
@RestController
@Tag(name = "Organizations", description = "Organizations the caller belongs to and their settings")
@RequestMapping("/api/organizations")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService organizations;

    @Operation(summary = "Create an organization; the caller becomes its ADMIN",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "201", description = "Organization created, Location header set")
    @ApiResponse(responseCode = "400", description = "Invalid input")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
    @ApiResponse(responseCode = "409", description = "Slug already taken")
    @PostMapping
    ResponseEntity<OrganizationResponse> create(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateOrganizationRequest request) {
        OrganizationResponse created = organizations.create(jwt.getSubject(), request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @Operation(summary = "List the organizations the caller belongs to",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "One page of organizations, sorted by name")
    @ApiResponse(responseCode = "400", description = "Page or size out of range")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
    @GetMapping
    PageResponse<OrganizationResponse> list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "" + PageResponse.DEFAULT_SIZE) @Min(1) @Max(PageResponse.MAX_SIZE) int size) {
        return organizations.listForCaller(jwt.getSubject(), page, size);
    }

    @Operation(summary = "Get one organization (members only)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "The organization")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
    @ApiResponse(responseCode = "404", description = "Not found, or the caller is not a member")
    @GetMapping("/{id}")
    OrganizationResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return organizations.get(jwt.getSubject(), id);
    }

    @Operation(summary = "Rename an organization (ADMIN of it)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "The renamed organization")
    @ApiResponse(responseCode = "400", description = "Invalid input")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
    @ApiResponse(responseCode = "403", description = "The caller is an AGENT of this organization")
    @ApiResponse(responseCode = "404", description = "Not found, or the caller is not a member")
    @PatchMapping("/{id}")
    OrganizationResponse rename(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @Valid @RequestBody UpdateOrganizationRequest request) {
        return organizations.rename(jwt.getSubject(), id, request);
    }

    @Operation(summary = "Delete an organization and its memberships (ADMIN of it)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "204", description = "Organization deleted")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
    @ApiResponse(responseCode = "403", description = "The caller is an AGENT of this organization")
    @ApiResponse(responseCode = "404", description = "Not found, or the caller is not a member")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        organizations.delete(jwt.getSubject(), id);
    }
}
