package com.connecta.backend.user.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateUserRequest(@NotNull Boolean enabled) {
}
