package com.connecta.backend.org.service;

import java.util.UUID;

/** Published when an organization is deleted, so features holding live state for it can let go. */
public record OrganizationDeletedEvent(UUID organizationId) {
}
