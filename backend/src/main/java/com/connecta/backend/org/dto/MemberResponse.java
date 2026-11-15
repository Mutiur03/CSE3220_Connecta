package com.connecta.backend.org.dto;

import com.connecta.backend.org.domain.Membership;
import com.connecta.backend.user.domain.User;
import java.time.Instant;
import java.util.UUID;

/** One member of an organization. Only the fields a teammate needs; never the password hash. */
public record MemberResponse(UUID membershipId, UUID userId, String email, String name, String role,
        Instant joinedAt) {

    public static MemberResponse from(Membership membership) {
        User user = membership.getUser();
        return new MemberResponse(membership.getId(), user.getId(), user.getEmail(), user.getFullName(),
                membership.getRole().getName(), membership.getCreatedAt());
    }
}
