package com.connecta.backend.org.controller;

import com.connecta.backend.common.dto.PageResponse;
import com.connecta.backend.org.dto.AcceptInvitationRequest;
import com.connecta.backend.org.dto.CreateInvitationRequest;
import com.connecta.backend.org.dto.InvitationCreatedResponse;
import com.connecta.backend.org.dto.InvitationResponse;
import com.connecta.backend.org.dto.OrganizationResponse;
import com.connecta.backend.org.service.InvitationService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Role rules (checked by InvitationService through TenantAccessService): create, list and revoke need
 * OWNER or ADMIN of that organization, and an invitation cannot grant a role above the caller's own.
 * Accept needs only a signed-in account whose email matches the invitation. Anyone else gets 403 (role
 * too low) or 404 (not a member, or no matching invitation).
 */
@RestController
@Tag(name = "Invitations", description = "Invite people to an organization and accept invitations")
@RequiredArgsConstructor
public class InvitationController {

    private final InvitationService invitations;

    @Operation(summary = "Invite an email to the organization (OWNER or ADMIN; cannot invite above own role)",
            description = "The raw token is returned once in this response. No email is sent yet, so the "
                    + "admin must pass the token on. Only its hash is stored.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "201", description = "Invitation created, Location header set")
    @ApiResponse(responseCode = "400", description = "Invalid email or role")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
    @ApiResponse(responseCode = "403", description = "Caller is an AGENT, or invites a role above their own")
    @ApiResponse(responseCode = "404", description = "Not found, or the caller is not a member")
    @ApiResponse(responseCode = "409", description = "A pending invitation exists, or the email is already a member")
    @PostMapping("/api/organizations/{organizationId}/invitations")
    ResponseEntity<InvitationCreatedResponse> create(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID organizationId, @Valid @RequestBody CreateInvitationRequest request) {
        InvitationCreatedResponse created = invitations.create(jwt.getSubject(), organizationId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.invitation().id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @Operation(summary = "List the organization's invitations, newest first (OWNER or ADMIN)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "One page of invitations; never includes tokens")
    @ApiResponse(responseCode = "400", description = "Page or size out of range")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
    @ApiResponse(responseCode = "403", description = "Caller is an AGENT of this organization")
    @ApiResponse(responseCode = "404", description = "Not found, or the caller is not a member")
    @GetMapping("/api/organizations/{organizationId}/invitations")
    PageResponse<InvitationResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID organizationId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "" + PageResponse.DEFAULT_SIZE) @Min(1) @Max(PageResponse.MAX_SIZE) int size) {
        return invitations.list(jwt.getSubject(), organizationId, page, size);
    }

    @Operation(summary = "Revoke a pending invitation (OWNER or ADMIN)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "204", description = "Invitation revoked")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
    @ApiResponse(responseCode = "403", description = "Caller is an AGENT of this organization")
    @ApiResponse(responseCode = "404", description = "Not found, another organization's invitation, or not a member")
    @ApiResponse(responseCode = "409", description = "The invitation was already accepted")
    @DeleteMapping("/api/organizations/{organizationId}/invitations/{invitationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revoke(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID organizationId, @PathVariable UUID invitationId) {
        invitations.revoke(jwt.getSubject(), organizationId, invitationId);
    }

    @Operation(summary = "Accept an invitation with its token; joins the organization with the invited role",
            description = "Any signed-in account whose email matches the invitation. Returns the organization.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "Joined; the organization as seen by the caller")
    @ApiResponse(responseCode = "400", description = "Missing token")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
    @ApiResponse(responseCode = "404", description = "No invitation matches this token for the caller's email")
    @ApiResponse(responseCode = "409", description = "Already accepted, or already a member of the organization")
    @ApiResponse(responseCode = "422", description = "The invitation expired or was revoked")
    @PostMapping("/api/invitations/accept")
    OrganizationResponse accept(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AcceptInvitationRequest request) {
        return invitations.accept(jwt.getSubject(), request);
    }
}
