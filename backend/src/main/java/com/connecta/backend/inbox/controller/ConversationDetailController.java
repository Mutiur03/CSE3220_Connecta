package com.connecta.backend.inbox.controller;

import com.connecta.backend.common.dto.PageResponse;
import com.connecta.backend.inbox.dto.ConversationDetailResponse;
import com.connecta.backend.inbox.dto.MessageResponse;
import com.connecta.backend.inbox.dto.UpdateConversationStatusRequest;
import com.connecta.backend.inbox.dto.UpdateReadStateRequest;
import com.connecta.backend.inbox.service.ConversationDetailService;
import com.connecta.backend.inbox.service.MessageHistoryService;
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
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Role rule: any member of the organization (ADMIN or AGENT) may read and update. The URL rule in
 * SecurityConfig is "authenticated"; the service checks the membership. A non-member, an unknown
 * conversation and another tenant's conversation all get 404.
 */
@RestController
@Tag(name = "Conversations", description = "One conversation and its message history")
@RequestMapping("/api/organizations/{organizationId}/conversations/{conversationId}")
@RequiredArgsConstructor
public class ConversationDetailController {

    private final ConversationDetailService conversations;
    private final MessageHistoryService history;

    @Operation(summary = "Get one conversation with its participants (any member)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "The conversation")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
    @ApiResponse(responseCode = "404", description = "Not found, another organization's, or not a member")
    @GetMapping
    ConversationDetailResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID organizationId,
            @PathVariable UUID conversationId) {
        return conversations.get(jwt.getSubject(), organizationId, conversationId);
    }

    @Operation(summary = "Page through the conversation's messages, newest first (any member)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "One page of messages")
    @ApiResponse(responseCode = "400", description = "Page or size out of range")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
    @ApiResponse(responseCode = "404", description = "Not found, another organization's, or not a member")
    @GetMapping("/messages")
    PageResponse<MessageResponse> messages(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID organizationId,
            @PathVariable UUID conversationId, @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "" + PageResponse.DEFAULT_SIZE) @Min(1) @Max(PageResponse.MAX_SIZE) int size) {
        return history.list(jwt.getSubject(), organizationId, conversationId, page, size);
    }

    @Operation(summary = "Set the conversation's status (any member)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "The updated conversation")
    @ApiResponse(responseCode = "400", description = "Missing or unknown status")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
    @ApiResponse(responseCode = "404", description = "Not found, another organization's, or not a member")
    @PatchMapping
    ConversationDetailResponse updateStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID organizationId,
            @PathVariable UUID conversationId, @Valid @RequestBody UpdateConversationStatusRequest request) {
        return conversations.updateStatus(jwt.getSubject(), organizationId, conversationId, request.status());
    }

    @Operation(summary = "Mark the conversation read or unread for the team (any member)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "204", description = "Read state saved")
    @ApiResponse(responseCode = "400", description = "Missing unread flag")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
    @ApiResponse(responseCode = "404", description = "Not found, another organization's, or not a member")
    @PutMapping("/read-state")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void updateReadState(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID organizationId,
            @PathVariable UUID conversationId, @Valid @RequestBody UpdateReadStateRequest request) {
        conversations.updateReadState(jwt.getSubject(), organizationId, conversationId, request.unread());
    }
}
