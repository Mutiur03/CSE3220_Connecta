package com.connecta.backend.analytics.controller;

import com.connecta.backend.analytics.dto.AgentAnalyticsResponse;
import com.connecta.backend.analytics.service.AgentAnalyticsService;
import com.connecta.backend.common.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Role rule: ADMIN of the organization (OWNER inherits), stated in SecurityConfig and on the service. An AGENT
 * gets 403, a non-member or another organization's id gets 404.
 */
@RestController
@Tag(name = "Analytics", description = "Conversation and agent metrics for a date range")
@RequiredArgsConstructor
public class AgentAnalyticsController {

    private final AgentAnalyticsService analytics;

    @Operation(summary = "Per-member performance for a date range (ADMIN)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "One page of members, sorted by name, with their metrics")
    @ApiResponse(responseCode = "400",
            description = "Malformed date, from after to, range over 366 days, unknown zone, or page out of range")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
    @ApiResponse(responseCode = "403", description = "Not an ADMIN")
    @ApiResponse(responseCode = "404", description = "Organization not found, or not a member of it")
    @GetMapping("/api/organizations/{organizationId}/analytics/agents")
    AgentAnalyticsResponse agents(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID organizationId,
            @Parameter(description = "First day, inclusive (default: 29 days before to)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Last day, inclusive (default: today in zone)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "IANA time zone id for day boundaries (default: UTC)")
            @RequestParam(required = false) String zone,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "" + PageResponse.DEFAULT_SIZE) @Min(1) @Max(PageResponse.MAX_SIZE) int size) {
        return analytics.agents(jwt.getSubject(), organizationId, from, to, zone, page, size);
    }
}
