package com.connecta.backend.org.dto;

import jakarta.validation.constraints.NotNull;

public record SetAssignableRequest(@NotNull Boolean assignable) {
}
