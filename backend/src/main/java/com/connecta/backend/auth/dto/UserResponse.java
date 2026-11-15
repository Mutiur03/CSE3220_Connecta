package com.connecta.backend.auth.dto;

import com.connecta.backend.user.domain.User;
import java.util.Set;
import java.util.UUID;

public record UserResponse(UUID id, String email, String fullName, Set<String> roles) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRoleNames());
    }
}
