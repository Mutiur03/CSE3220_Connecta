package com.connecta.backend.org.dto;

import com.connecta.backend.org.domain.Invitation;
import com.connecta.backend.user.domain.Roles;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateInvitationRequest(
        @NotBlank @Email @Size(max = Invitation.EMAIL_MAX_LENGTH) String email,
        @NotNull @Pattern(regexp = "^(" + Roles.OWNER + "|" + Roles.ADMIN + "|" + Roles.AGENT + ")$") String role) {
}
