package com.connecta.backend.org.controller;

import com.connecta.backend.common.dto.PageResponse;
import com.connecta.backend.org.dto.MemberResponse;
import com.connecta.backend.org.dto.UpdateMemberRoleRequest;
import com.connecta.backend.org.service.MemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Role rules (checked by MemberService through TenantAccessService). List needs any membership of that
 * organization. Changing a role needs ADMIN or above. Removing another member needs ADMIN or above; any
 * member may remove their own membership. A role or member above the caller's own is 403, and the last
 * ADMIN can neither be demoted nor removed (422).
 */
@RestController
@Tag(name = "Members", description = "Who belongs to an organization and which role each member holds")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService members;

    @Operation(summary = "List the organization's members, by name (any member)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "One page of members; no password data")
    @ApiResponse(responseCode = "400", description = "Page or size out of range")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
    @ApiResponse(responseCode = "404", description = "Not found, or the caller is not a member")
    @GetMapping("/api/organizations/{organizationId}/members")
    PageResponse<MemberResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID organizationId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "" + PageResponse.DEFAULT_SIZE) @Min(1) @Max(PageResponse.MAX_SIZE) int size) {
        return members.list(jwt.getSubject(), organizationId, page, size);
    }

    @Operation(summary = "Change a member's role (ADMIN of it; ADMIN or AGENT; keeps one ADMIN)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "Role changed; the member as now stored")
    @ApiResponse(responseCode = "400", description = "Role is not ADMIN or AGENT")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
    @ApiResponse(responseCode = "403", description = "Caller is an AGENT, grants a role above their own, or the member outranks them")
    @ApiResponse(responseCode = "404", description = "Not found, another organization's member, or not a member")
    @ApiResponse(responseCode = "422", description = "The member is the last ADMIN and would be demoted")
    @PatchMapping("/api/organizations/{organizationId}/members/{membershipId}")
    MemberResponse changeRole(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID organizationId,
            @PathVariable UUID membershipId, @Valid @RequestBody UpdateMemberRoleRequest request) {
        return members.changeRole(jwt.getSubject(), organizationId, membershipId, request.role());
    }

    @Operation(summary = "Remove a member, or leave yourself (self: any role; others: ADMIN or above, not above own role)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "204", description = "Member removed or caller left the organization")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
    @ApiResponse(responseCode = "403", description = "Caller is an AGENT removing someone else, or the member outranks them")
    @ApiResponse(responseCode = "404", description = "Not found, another organization's member, or not a member")
    @ApiResponse(responseCode = "422", description = "The member is the last ADMIN")
    @DeleteMapping("/api/organizations/{organizationId}/members/{membershipId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID organizationId, @PathVariable UUID membershipId) {
        members.remove(jwt.getSubject(), organizationId, membershipId);
    }
}
