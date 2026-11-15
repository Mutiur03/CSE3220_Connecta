package com.connecta.backend.org.service;

import com.connecta.backend.org.domain.Membership;
import com.connecta.backend.org.repository.MembershipRepository;
import com.connecta.backend.user.domain.Roles;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one place that decides whether a user may act inside an organization (a tenant).
 *
 * <p>Other features call this service, never {@code MembershipRepository} directly, so the tenant rule and
 * the role order live in one place. Callers pass the user id from the JWT subject, as {@code AuthController}
 * does. An unknown organization and a non-member both raise {@link NotAMemberException}.
 *
 * <p>The role order mirrors the {@code RoleHierarchy} bean in {@code SecurityConfig}; change both together.
 */
@Service
@RequiredArgsConstructor
public class TenantAccessService {

    /** Highest role first: each role includes every role after it. */
    private static final List<String> ROLES_HIGHEST_FIRST = List.of(Roles.OWNER, Roles.ADMIN, Roles.AGENT);

    private final MembershipRepository memberships;

    /** The user's membership in the organization. Throws {@link NotAMemberException} otherwise. */
    @Transactional(readOnly = true)
    public Membership requireMembership(UUID organizationId, UUID userId) {
        return memberships.findByOrganizationIdAndUserId(organizationId, userId)
                .orElseThrow(NotAMemberException::new);
    }

    /**
     * The membership, when the user holds {@code requiredRole} there or a higher one. A member with a lower
     * role gets {@link InsufficientOrganizationRoleException}; a non-member gets {@link NotAMemberException}.
     */
    @Transactional(readOnly = true)
    public Membership requireRole(UUID organizationId, UUID userId, String requiredRole) {
        Membership membership = requireMembership(organizationId, userId);
        if (!hasAtLeast(membership, requiredRole)) {
            throw new InsufficientOrganizationRoleException();
        }
        return membership;
    }

    /** True when the membership's role is {@code requiredRole} or above it. An unknown role held is never enough. */
    public boolean hasAtLeast(Membership membership, String requiredRole) {
        int required = ROLES_HIGHEST_FIRST.indexOf(requiredRole);
        if (required < 0) {
            throw new IllegalArgumentException("Unknown organization role: " + requiredRole);
        }
        int held = ROLES_HIGHEST_FIRST.indexOf(membership.getRole().getName());
        return held >= 0 && held <= required;
    }
}
