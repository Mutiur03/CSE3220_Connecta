package com.connecta.backend.user.controller;

import com.connecta.backend.common.dto.PageResponse;
import com.connecta.backend.user.dto.AdminUserResponse;
import com.connecta.backend.user.dto.UpdateUserRequest;
import com.connecta.backend.user.service.UserService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Role rule: OWNER only, stated twice (a URL rule in SecurityConfig and @PreAuthorize on the service).
 * ADMIN and AGENT get 403.
 */
@RestController
@Tag(name = "Admin", description = "System owner views across all organizations")
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserService users;

    @Operation(summary = "List every user account (OWNER only)", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "One page of users, the caller first, then sorted by email")
    @ApiResponse(responseCode = "400", description = "Page or size out of range")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
    @ApiResponse(responseCode = "403", description = "The caller is not the system OWNER")
    @GetMapping
    PageResponse<AdminUserResponse> list(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "" + PageResponse.DEFAULT_SIZE) @Min(1) @Max(PageResponse.MAX_SIZE) int size) {
        return users.listAll(UUID.fromString(jwt.getSubject()), page, size);
    }

    @Operation(summary = "Enable or disable a user account (OWNER only)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "The updated user; repeating the same change is a no-op")
    @ApiResponse(responseCode = "400", description = "enabled is missing")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
    @ApiResponse(responseCode = "403", description = "The caller is not the system OWNER")
    @ApiResponse(responseCode = "404", description = "No such user")
    @ApiResponse(responseCode = "422", description = "The system owner cannot be disabled")
    @PatchMapping("/{userId}")
    AdminUserResponse update(@PathVariable UUID userId, @Valid @RequestBody UpdateUserRequest request) {
        return users.changeEnabled(userId, request.enabled());
    }
}
