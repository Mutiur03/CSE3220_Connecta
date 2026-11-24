package com.connecta.backend.inbox.service;

import java.util.UUID;

/**
 * Published (Observer) once after a history import stored messages for an organization. Realtime turns it into
 * one refresh push, instead of one push per imported message.
 */
public record HistoryImportedEvent(UUID organizationId) {
}
