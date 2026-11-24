package com.connecta.backend.realtime.controller;

import com.connecta.backend.realtime.dto.TicketResponse;
import com.connecta.backend.realtime.service.RealtimeTicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Role rule: any member of the organization (ADMIN or AGENT). The URL rule is "authenticated"; the service
 * checks the membership. A non-member and an unknown organization both get 404.
 */
@RestController
@Tag(name = "Realtime", description = "Tickets for the live inbox WebSocket")
@RequestMapping("/api/organizations/{organizationId}/realtime/tickets")
@RequiredArgsConstructor
public class RealtimeTicketController {

    private final RealtimeTicketService tickets;

    @Operation(summary = "Get a single-use ticket to open /ws/inbox (any member)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "201", description = "The ticket and its lifetime in seconds")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
    @ApiResponse(responseCode = "404", description = "Unknown organization or not a member")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    TicketResponse issue(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID organizationId) {
        return tickets.issue(jwt.getSubject(), organizationId);
    }
}
