package com.connecta.backend.org.dto;

import com.connecta.backend.org.domain.AgentAvailability;
import com.connecta.backend.org.domain.AvailabilityStatus;
import com.connecta.backend.org.domain.Membership;
import com.connecta.backend.user.domain.User;
import java.time.Instant;
import java.util.UUID;

/**
 * One member, whether they take new conversations, and how many open conversations they hold.
 * {@code updatedAt} is null until they first set it.
 * {@code online} means connected now or inside the grace period after the last connection dropped.
 */
public record AvailabilityResponse(UUID membershipId, UUID userId, String email, String name, String role,
        AvailabilityStatus status, boolean assignable, Instant updatedAt, long openConversations,
        boolean online) {

    /** {@code availability} may be null: a member without a row counts as AWAY and not assignable. */
    public static AvailabilityResponse from(Membership membership, AgentAvailability availability,
            long openConversations, boolean online) {
        User user = membership.getUser();
        return new AvailabilityResponse(membership.getId(), user.getId(), user.getEmail(), user.getFullName(),
                membership.getRole().getName(),
                availability == null ? AvailabilityStatus.AWAY : availability.getStatus(),
                availability != null && availability.isAssignable(),
                availability == null ? null : availability.getUpdatedAt(), openConversations, online);
    }
}
