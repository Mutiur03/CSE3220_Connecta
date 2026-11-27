package com.connecta.backend.org.dto;

import java.util.UUID;

/** A member's assignable switch as stored. */
public record AssignableResponse(UUID membershipId, boolean assignable) {
}
