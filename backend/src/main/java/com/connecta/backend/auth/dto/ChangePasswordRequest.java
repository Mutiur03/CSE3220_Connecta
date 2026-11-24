package com.connecta.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        // Only compared with the stored hash, so no minimum: an older, shorter password must still work.
        @NotBlank @Size(max = 72) String currentPassword,
        // Same limits as RegisterRequest: 72 is BCrypt's input limit.
        @NotBlank @Size(min = 8, max = 72) String newPassword) {
}
