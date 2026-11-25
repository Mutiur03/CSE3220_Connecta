package com.connecta.backend.org.service;

import java.util.UUID;

/** Published when a user stops being a member of an organization (removed or left). */
public record MemberRemovedEvent(UUID organizationId, UUID userId) {
}
