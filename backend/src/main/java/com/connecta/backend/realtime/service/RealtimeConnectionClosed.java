package com.connecta.backend.realtime.service;

import java.util.UUID;

/** A realtime session for this user in this organization is gone, however it ended. */
public record RealtimeConnectionClosed(UUID userId, UUID organizationId) {
}
