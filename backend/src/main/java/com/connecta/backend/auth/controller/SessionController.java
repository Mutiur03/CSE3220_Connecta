package com.connecta.backend.auth.controller;

import com.connecta.backend.auth.dto.SessionResponse;
import com.connecta.backend.auth.service.SessionService;
import com.connecta.backend.auth.service.TokenService;
import com.connecta.backend.common.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in user's devices. Any authenticated user, own sessions only (default-deny URL rule). */
@RestController
@Tag(name = "Sessions", description = "List and revoke the caller's signed-in devices")
@RequestMapping("/api/auth/sessions")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class SessionController {

    private final SessionService sessions;

    @Operation(summary = "List my active sessions, most recently used first")
    @ApiResponse(responseCode = "200", description = "A page of sessions")
    @ApiResponse(responseCode = "401", description = "Missing or invalid access token")
    @GetMapping
    public PageResponse<SessionResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "" + PageResponse.DEFAULT_SIZE) @Min(1) @Max(PageResponse.MAX_SIZE) int size) {
        UUID current = currentSession(jwt);
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "lastUsedAt", "id"));
        return PageResponse.from(sessions.activeSessions(UUID.fromString(jwt.getSubject()), pageable),
                token -> SessionResponse.from(token, current));
    }

    @Operation(summary = "Revoke one of my sessions")
    @ApiResponse(responseCode = "204", description = "Session revoked")
    @ApiResponse(responseCode = "401", description = "Missing or invalid access token")
    @ApiResponse(responseCode = "404", description = "No such session for this user")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        sessions.revoke(UUID.fromString(jwt.getSubject()), id);
    }

    @Operation(summary = "Revoke every session except the current one")
    @ApiResponse(responseCode = "204", description = "Other sessions revoked")
    @ApiResponse(responseCode = "401", description = "Missing or invalid access token")
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeOthers(@AuthenticationPrincipal Jwt jwt) {
        sessions.revokeOthers(UUID.fromString(jwt.getSubject()), currentSession(jwt));
    }

    // A token minted before sessions existed has no sid: current is then unknown and revokeOthers revokes nothing.
    private static UUID currentSession(Jwt jwt) {
        return TokenService.sessionId(jwt);
    }
}
