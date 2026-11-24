package com.connecta.backend.realtime.service;

import java.time.Instant;
import java.util.UUID;

/** What a redeemed ticket proves: this user may listen to this organization until the ticket would have expired. */
public record RealtimeGrant(UUID organizationId, UUID userId, Instant expiresAt) {
}
