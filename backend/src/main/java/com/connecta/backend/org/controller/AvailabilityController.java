package com.connecta.backend.org.controller;

import com.connecta.backend.common.dto.PageResponse;
import com.connecta.backend.org.dto.AvailabilityReportResponse;
import com.connecta.backend.org.dto.AvailabilityResponse;
import com.connecta.backend.org.dto.SetAvailabilityRequest;
import com.connecta.backend.org.service.AgentAvailabilityService;
import com.connecta.backend.org.service.AvailabilityReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Role rules (checked by AgentAvailabilityService through TenantAccessService). Reading or setting your
 * own status needs any membership of the organization (non-members get 404). Listing everyone's status
 * and the time report need ADMIN or above (AGENT gets 403).
 */
@RestController
@Tag(name = "Availability", description = "Whether a member takes new conversations")
@RequiredArgsConstructor
public class AvailabilityController {

    private final AgentAvailabilityService availability;
    private final AvailabilityReportService report;

    @Operation(summary = "Your own availability, AWAY if never set (any member)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "The caller's availability")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
    @ApiResponse(responseCode = "404", description = "Not found, or the caller is not a member")
    @GetMapping("/api/organizations/{organizationId}/availability/me")
    AvailabilityResponse getOwn(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID organizationId) {
        return availability.getOwn(jwt.getSubject(), organizationId);
    }

    @Operation(summary = "Set your own availability (any member)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "The caller's availability as now stored")
    @ApiResponse(responseCode = "400", description = "Status is not AVAILABLE or AWAY")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
    @ApiResponse(responseCode = "404", description = "Not found, or the caller is not a member")
    @PutMapping("/api/organizations/{organizationId}/availability/me")
    AvailabilityResponse setOwn(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID organizationId,
            @Valid @RequestBody SetAvailabilityRequest request) {
        return availability.setOwn(jwt.getSubject(), organizationId, request.toStatus());
    }

    @Operation(summary = "Every member's availability, by name (ADMIN of it)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "One page of members with their status")
    @ApiResponse(responseCode = "400", description = "Page or size out of range")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
    @ApiResponse(responseCode = "403", description = "Caller is an AGENT")
    @ApiResponse(responseCode = "404", description = "Not found, or the caller is not a member")
    @GetMapping("/api/organizations/{organizationId}/availability")
    PageResponse<AvailabilityResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID organizationId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "" + PageResponse.DEFAULT_SIZE) @Min(1) @Max(PageResponse.MAX_SIZE) int size) {
        return availability.list(jwt.getSubject(), organizationId, page, size);
    }

    @Operation(summary = "Seconds each member was available per UTC day, from to inclusive, max 31 days (ADMIN of it)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "One page of members, each with a zero-filled day list")
    @ApiResponse(responseCode = "400",
            description = "Missing or malformed date, from after to, over 31 days, or page out of range")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
    @ApiResponse(responseCode = "403", description = "Caller is an AGENT")
    @ApiResponse(responseCode = "404", description = "Not found, or the caller is not a member")
    @GetMapping("/api/organizations/{organizationId}/availability/report")
    AvailabilityReportResponse report(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID organizationId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "" + PageResponse.DEFAULT_SIZE) @Min(1) @Max(PageResponse.MAX_SIZE) int size) {
        return report.report(jwt.getSubject(), organizationId, from, to, page, size);
    }
}
