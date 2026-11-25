package com.connecta.backend.inbox.controller;

import com.connecta.backend.inbox.dto.MessageResponse;
import com.connecta.backend.inbox.service.AttachmentUpload;
import com.connecta.backend.inbox.service.OutgoingAttachmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Role rule: any member of the organization (ADMIN or AGENT) may send a file. The URL rule in SecurityConfig is
 * "authenticated"; the service checks the membership. A non-member and another tenant's conversation get 404.
 */
@RestController
@Tag(name = "Conversations", description = "Send a reply in a conversation")
@RequestMapping("/api/organizations/{organizationId}/conversations/{conversationId}/messages")
@RequiredArgsConstructor
public class OutgoingAttachmentController {

    private final OutgoingAttachmentService attachments;

    @Operation(summary = "Send a file, multipart field 'file', through the conversation's channel (any member)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "201", description = "Sent and stored, with the attachment")
    @ApiResponse(responseCode = "400", description = "The file part is missing or empty")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
    @ApiResponse(responseCode = "404", description = "Not found, another organization's, or not a member")
    @ApiResponse(responseCode = "413", description = "The file is larger than the limit")
    @ApiResponse(responseCode = "415", description = "The file type is not allowed or its content does not match")
    @ApiResponse(responseCode = "422", description = "Channel not connected, reply window closed, kind not "
            + "supported on the channel, or PUBLIC_BASE_URL not configured")
    @ApiResponse(responseCode = "502", description = "Meta could not deliver the message")
    @PostMapping(value = "/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<MessageResponse> send(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID organizationId,
            @PathVariable UUID conversationId, @RequestParam("file") MultipartFile file) {
        MessageResponse sent = attachments.send(jwt.getSubject(), organizationId, conversationId,
                new AttachmentUpload(bytesOf(file), file.getContentType(), file.getOriginalFilename()));
        URI location = URI.create("/api/organizations/" + organizationId + "/conversations/" + conversationId
                + "/messages/" + sent.id());
        return ResponseEntity.created(location).body(sent);
    }

    /** A read failure of the buffered upload is a server fault, answered as a generic 500 by the global handler. */
    private static byte[] bytesOf(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
