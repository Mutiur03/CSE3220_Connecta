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
 */
public record AvailabilityResponse(UUID membershipId, UUID userId, String email, String name, String role,
        AvailabilityStatus status, Instant updatedAt, long openConversations) {

    /** {@code availability} may be null: a member without a row counts as AWAY. */
    public static AvailabilityResponse from(Membership membership, AgentAvailability availability,
            long openConversations) {
        User user = membership.getUser();
        return new AvailabilityResponse(membership.getId(), user.getId(), user.getEmail(), user.getFullName(),
                membership.getRole().getName(),
                availability == null ? AvailabilityStatus.AWAY : availability.getStatus(),
                availability == null ? null : availability.getUpdatedAt(), openConversations);
    }
}
