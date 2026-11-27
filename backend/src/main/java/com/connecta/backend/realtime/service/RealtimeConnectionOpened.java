package com.connecta.backend.realtime.service;

import java.util.UUID;

/** A realtime session for this user in this organization was registered. */
public record RealtimeConnectionOpened(UUID userId, UUID organizationId) {
}
