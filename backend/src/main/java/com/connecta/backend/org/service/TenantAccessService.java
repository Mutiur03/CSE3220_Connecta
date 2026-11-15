package com.connecta.backend.org.service;

import com.connecta.backend.org.domain.Membership;
import com.connecta.backend.org.repository.MembershipRepository;
import com.connecta.backend.user.domain.Roles;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one place that decides whether a user may act inside an organization (a tenant).
 *
 * <p>Other features call this service, never {@code MembershipRepository} directly, so the tenant rule and
 * the role order live in one place. Callers pass the user id from the JWT subject, as {@code AuthController}
 * does. An unknown organization and a non-member both raise {@link NotAMemberException}.
 *
 * <p>The role order comes from the {@code RoleHierarchy} bean in {@code SecurityConfig}, the single source.
 */
@Service
@RequiredArgsConstructor
public class TenantAccessService {

    private final MembershipRepository memberships;

    private final RoleHierarchy roleHierarchy;

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

    /** True when the user has a membership in the organization, whatever its role. */
    @Transactional(readOnly = true)
    public boolean isMember(UUID organizationId, UUID userId) {
        return memberships.findByOrganizationIdAndUserId(organizationId, userId).isPresent();
    }

    /** True when the membership's role is {@code requiredRole} or reaches it through the role hierarchy. */
    public boolean hasAtLeast(Membership membership, String requiredRole) {
        String held = Roles.AUTHORITY_PREFIX + membership.getRole().getName();
        String required = Roles.AUTHORITY_PREFIX + requiredRole;
        return roleHierarchy.getReachableGrantedAuthorities(List.of(new SimpleGrantedAuthority(held))).stream()
                .anyMatch(authority -> required.equals(authority.getAuthority()));
    }
}
