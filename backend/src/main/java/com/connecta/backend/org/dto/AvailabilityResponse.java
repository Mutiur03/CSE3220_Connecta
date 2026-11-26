package com.connecta.backend.org.dto;

import com.connecta.backend.org.domain.AgentAvailability;
import com.connecta.backend.org.domain.Membership;
import com.connecta.backend.user.domain.User;
import java.time.Instant;
import java.util.UUID;

/**
 * One member, whether auto-assignment may pick them, and how many open conversations they hold.
 * {@code updatedAt} is null until the row exists.
 * {@code online} means connected now or inside the grace period after the last connection dropped.
 */
public record AvailabilityResponse(UUID membershipId, UUID userId, String email, String name, String role,
        boolean assignable, Instant updatedAt, long openConversations,
        boolean online) {

    /** {@code availability} may be null: a member without a row is not assignable. */
    public static AvailabilityResponse from(Membership membership, AgentAvailability availability,
            long openConversations, boolean online) {
        User user = membership.getUser();
        return new AvailabilityResponse(membership.getId(), user.getId(), user.getEmail(), user.getFullName(),
                membership.getRole().getName(),
                availability != null && availability.isAssignable(),
                availability == null ? null : availability.getUpdatedAt(), openConversations, online);
    }
}
