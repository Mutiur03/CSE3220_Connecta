package com.connecta.backend.org.dto;

import com.connecta.backend.org.domain.Membership;
import com.connecta.backend.org.domain.Organization;
import java.time.Instant;
import java.util.UUID;

/** An organization as seen by one caller. {@code callerRole} is the caller's role in it (OWNER, ADMIN or AGENT). */
public record OrganizationResponse(UUID id, String name, String slug, String callerRole, Instant createdAt) {

    public static OrganizationResponse from(Membership membership) {
        Organization organization = membership.getOrganization();
        return new OrganizationResponse(organization.getId(), organization.getName(), organization.getSlug(),
                membership.getRole().getName(), organization.getCreatedAt());
    }
}
