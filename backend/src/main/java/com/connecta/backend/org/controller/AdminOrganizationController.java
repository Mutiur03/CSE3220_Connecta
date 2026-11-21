package com.connecta.backend.org.controller;

import com.connecta.backend.common.dto.PageResponse;
import com.connecta.backend.org.dto.OrganizationSummaryResponse;
import com.connecta.backend.org.service.OrganizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Role rule: OWNER only, stated twice (a URL rule in SecurityConfig and @PreAuthorize on the service).
 * The system owner sees every organization; ADMIN and AGENT get 403.
 */
@RestController
@Tag(name = "Admin", description = "System owner views across all organizations")
@RequestMapping("/api/admin/organizations")
@RequiredArgsConstructor
public class AdminOrganizationController {

    private final OrganizationService organizations;

    @Operation(summary = "List every organization with its member count (OWNER only)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "One page of organizations, sorted by name")
    @ApiResponse(responseCode = "400", description = "Page or size out of range")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
    @ApiResponse(responseCode = "403", description = "The caller is not the system OWNER")
    @GetMapping
    PageResponse<OrganizationSummaryResponse> list(@RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "" + PageResponse.DEFAULT_SIZE) @Min(1) @Max(PageResponse.MAX_SIZE) int size) {
        return organizations.listAll(page, size);
    }
}
