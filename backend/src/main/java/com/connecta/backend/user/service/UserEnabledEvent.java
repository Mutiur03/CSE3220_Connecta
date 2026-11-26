package com.connecta.backend.user.service;

import java.util.UUID;

/** Published when a disabled user account is enabled again. */
public record UserEnabledEvent(UUID userId) {
}
