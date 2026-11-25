package com.connecta.backend.user.dto;

import com.connecta.backend.user.domain.User;
import java.util.List;
import java.util.UUID;

/** A user as the system owner sees them. Never carries the password hash. */
public record AdminUserResponse(UUID id, String email, String fullName, boolean enabled, List<String> roles) {

    public static AdminUserResponse from(User user) {
        return new AdminUserResponse(user.getId(), user.getEmail(), user.getFullName(), user.isEnabled(),
                user.getRoleNames().stream().sorted().toList());
    }
}
