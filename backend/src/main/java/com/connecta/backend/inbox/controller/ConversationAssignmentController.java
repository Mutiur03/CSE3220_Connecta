package com.connecta.backend.inbox.controller;

import com.connecta.backend.common.dto.PageResponse;
import com.connecta.backend.inbox.dto.AssignConversationRequest;
import com.connecta.backend.inbox.dto.ConversationAssigneeResponse;
import com.connecta.backend.inbox.dto.ConversationAssignmentResponse;
import com.connecta.backend.inbox.service.ConversationAssignmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Role rule: ADMIN of the organization (OWNER inherits), stated in SecurityConfig and on the service. An AGENT
 * gets 403. An unknown conversation, another tenant's conversation or member and a non-member get 404.
 */
@RestController
@Tag(name = "Conversation assignment", description = "Hand a conversation to a member and read the history")
@RequestMapping("/api/organizations/{organizationId}/conversations/{conversationId}")
@RequiredArgsConstructor
public class ConversationAssignmentController {

    private final ConversationAssignmentService assignments;

    @Operation(summary = "Assign, reassign or unassign (membershipId null) a conversation (ADMIN)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "The current assignee")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
    @ApiResponse(responseCode = "403", description = "Not an ADMIN")
    @ApiResponse(responseCode = "404", description = "Conversation or member not found, or another organization's")
    @ApiResponse(responseCode = "422", description = "Already assigned to that member, or the member is disabled")
    @PostMapping("/assignment")
    ConversationAssigneeResponse assign(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID organizationId,
            @PathVariable UUID conversationId, @Valid @RequestBody AssignConversationRequest request) {
        return assignments.assign(jwt.getSubject(), organizationId, conversationId, request.membershipId());
    }

    @Operation(summary = "Page through the assignment history, newest first (ADMIN)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "One page of hand-overs")
    @ApiResponse(responseCode = "400", description = "Page or size out of range")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
    @ApiResponse(responseCode = "403", description = "Not an ADMIN")
    @ApiResponse(responseCode = "404", description = "Conversation not found, or another organization's")
    @GetMapping("/assignments")
    PageResponse<ConversationAssignmentResponse> history(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID organizationId, @PathVariable UUID conversationId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "" + PageResponse.DEFAULT_SIZE) @Min(1) @Max(PageResponse.MAX_SIZE) int size) {
        return assignments.list(jwt.getSubject(), organizationId, conversationId, page, size);
    }
}
