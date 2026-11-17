package com.connecta.backend.org.dto;

import com.connecta.backend.org.repository.OrganizationSummary;
import java.time.Instant;
import java.util.UUID;

/** An organization as the system owner sees it: no caller role, plus how many members it has. */
public record OrganizationSummaryResponse(UUID id, String name, String slug, long memberCount, Instant createdAt) {

    public static OrganizationSummaryResponse from(OrganizationSummary summary) {
        return new OrganizationSummaryResponse(summary.getId(), summary.getName(), summary.getSlug(),
                summary.getMemberCount(), summary.getCreatedAt());
    }
}
