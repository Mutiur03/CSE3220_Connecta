package com.connecta.backend.setup.controller;

import com.connecta.backend.auth.dto.RegisterRequest;
import com.connecta.backend.auth.dto.UserResponse;
import com.connecta.backend.setup.dto.SetupStatusResponse;
import com.connecta.backend.setup.service.SetupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Role rule: both endpoints are public by design, because nobody can sign in before the owner exists.
 * POST closes itself: once the OWNER exists it answers 409 to everyone.
 */
@RestController
@Tag(name = "Setup", description = "First-run setup of the system owner")
@RequestMapping("/api/setup")
@RequiredArgsConstructor
public class SetupController {

    private final SetupService setup;

    @Operation(summary = "Whether the first-run setup is still needed (public)")
    @ApiResponse(responseCode = "200", description = "setupRequired is true until the owner exists")
    @GetMapping("/status")
    public SetupStatusResponse status() {
        return setup.status();
    }

    @Operation(summary = "Create the system OWNER (public, only until it exists)")
    @ApiResponse(responseCode = "201", description = "Owner created")
    @ApiResponse(responseCode = "400", description = "Invalid input")
    @ApiResponse(responseCode = "409", description = "Setup is already complete, or the email is in use")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse completeSetup(@Valid @RequestBody RegisterRequest request) {
        return setup.completeSetup(request);
    }
}
