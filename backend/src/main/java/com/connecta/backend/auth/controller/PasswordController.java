package com.connecta.backend.auth.controller;

import com.connecta.backend.auth.dto.ChangePasswordRequest;
import com.connecta.backend.auth.service.PasswordService;
import com.connecta.backend.auth.service.TokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in user's own password. Any authenticated user; the account comes from the token. */
@RestController
@Tag(name = "Password", description = "Change the caller's password")
@RequestMapping("/api/auth/password")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class PasswordController {

    private final PasswordService passwords;

    @Operation(summary = "Change my password and sign out my other devices")
    @ApiResponse(responseCode = "204", description = "Password changed")
    @ApiResponse(responseCode = "400", description = "Invalid input")
    @ApiResponse(responseCode = "401", description = "Missing or invalid access token")
    @ApiResponse(responseCode = "422", description = "Wrong current password, or the new one is the same")
    @PutMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void change(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChangePasswordRequest request) {
        passwords.change(UUID.fromString(jwt.getSubject()), TokenService.sessionId(jwt),
                request.currentPassword(), request.newPassword());
    }
}
