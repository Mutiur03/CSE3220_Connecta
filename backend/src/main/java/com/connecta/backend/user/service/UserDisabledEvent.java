package com.connecta.backend.user.service;

import java.util.UUID;

/** Published when a user account is disabled. A future account deletion publishes the same event. */
public record UserDisabledEvent(UUID userId) {
}
