package com.connecta.backend.org.dto;

import com.connecta.backend.user.domain.Roles;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record UpdateMemberRoleRequest(
        @NotNull @Pattern(regexp = "^(" + Roles.ADMIN + "|" + Roles.AGENT + ")$") String role) {
}
