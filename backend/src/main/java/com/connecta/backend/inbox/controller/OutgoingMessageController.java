package com.connecta.backend.inbox.controller;

import com.connecta.backend.inbox.dto.MessageResponse;
import com.connecta.backend.inbox.dto.SendMessageRequest;
import com.connecta.backend.inbox.service.OutgoingMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Role rule: any member of the organization (ADMIN or AGENT) may send. The URL rule in SecurityConfig is
 * "authenticated"; the service checks the membership. A non-member and another tenant's conversation get 404.
 */
@RestController
@Tag(name = "Conversations", description = "Send a reply in a conversation")
@RequestMapping("/api/organizations/{organizationId}/conversations/{conversationId}/messages")
@RequiredArgsConstructor
public class OutgoingMessageController {

    private final OutgoingMessageService outgoing;

    @Operation(summary = "Send a reply through the conversation's channel (any member)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "201", description = "Sent and stored")
    @ApiResponse(responseCode = "400", description = "Blank or too long text")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
    @ApiResponse(responseCode = "404", description = "Not found, another organization's, or not a member")
    @ApiResponse(responseCode = "422", description = "Channel not connected, or Meta's reply window is closed")
    @ApiResponse(responseCode = "502", description = "Meta could not deliver the message")
    @PostMapping
    ResponseEntity<MessageResponse> send(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID organizationId,
            @PathVariable UUID conversationId, @Valid @RequestBody SendMessageRequest request) {
        MessageResponse sent = outgoing.send(jwt.getSubject(), organizationId, conversationId, request.text());
        URI location = URI.create("/api/organizations/" + organizationId + "/conversations/" + conversationId
                + "/messages/" + sent.id());
        return ResponseEntity.created(location).body(sent);
    }
}
