package com.connecta.backend.meta.service;

import java.util.UUID;

/**
 * Published once per organization for each new message from Meta (Observer). The inbox persists and shows it
 * (INBOX-02) and later features listen to it, without webhook processing knowing about them.
 */
public record MessageReceivedEvent(UUID organizationId, NormalizedMessage message) {
}
