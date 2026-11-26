package com.connecta.backend.org.service;

import com.connecta.backend.common.dto.PageResponse;
import com.connecta.backend.org.domain.Membership;
import com.connecta.backend.org.dto.MemberResponse;
import com.connecta.backend.org.repository.MembershipRepository;
import com.connecta.backend.user.domain.Roles;
import com.connecta.backend.user.service.UserService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Who belongs to an organization and which role each member holds. Membership and role checks go through
 * {@link TenantAccessService}; this service adds the ceiling rules. A caller can grant or touch a role only
 * up to their own, and the organization always keeps one ADMIN. Every member row is loaded by id AND
 * organization, so another tenant's membership id reads as not found.
 */
@Service
@RequiredArgsConstructor
public class MemberService {

    /** Stable order for lists: by name, then by membership id so equal names never swap places. */
    private static final Sort BY_NAME = Sort.by("user.fullName").ascending().and(Sort.by("id"));

    private final MembershipRepository memberships;
    private final UserService users;
    private final TenantAccessService tenants;
    private final ActiveCaller activeCaller;
    private final ApplicationEventPublisher events;

    /** Any member, whatever their role. */
    @Transactional(readOnly = true)
    public PageResponse<MemberResponse> list(String subject, UUID organizationId, int page, int size) {
        tenants.requireMembership(organizationId, activeCaller.load(subject).getId());
        Page<Membership> found =
                memberships.findByOrganizationId(organizationId, PageRequest.of(page, size, BY_NAME));
        return PageResponse.from(found, MemberResponse::from);
    }

    /**
     * ADMIN or above. The new role may not be above the caller's own, and the member's current role may not be
     * above it either. Demoting the last ADMIN is refused.
     */
    @Transactional
    public MemberResponse changeRole(String subject, UUID organizationId, UUID membershipId, String roleName) {
        Membership caller = tenants.requireRole(organizationId, activeCaller.load(subject).getId(), Roles.ADMIN);
        Membership target = findMember(organizationId, membershipId);
        requireCeiling(caller, target);
        if (!tenants.hasAtLeast(caller, roleName)) {
            throw new InsufficientOrganizationRoleException();
        }
        if (tenants.hasAtLeast(target, Roles.ADMIN) && !roleName.equals(Roles.ADMIN)) {
            requireAnotherAdmin(organizationId);
        }
        target.changeRole(users.findRole(roleName));
        return MemberResponse.from(target);
    }

    /**
     * Any member may remove their own membership (leave), whatever their role. Removing someone else needs
     * ADMIN or above and the same ceiling as a role change. The last ADMIN cannot be removed by anyone.
     */
    @Transactional
    public void remove(String subject, UUID organizationId, UUID membershipId) {
        UUID callerId = activeCaller.load(subject).getId();
        Membership self = tenants.requireMembership(organizationId, callerId);
        Membership target = self.getId().equals(membershipId)
                ? self
                : managedMember(organizationId, callerId, membershipId);
        if (tenants.hasAtLeast(target, Roles.ADMIN)) {
            requireAnotherAdmin(organizationId);
        }
        memberships.delete(target);
        events.publishEvent(new MemberRemovedEvent(organizationId, target.getUser().getId()));
    }

    /** The member, when the caller is ADMIN or above and outranks or equals them. */
    private Membership managedMember(UUID organizationId, UUID callerId, UUID membershipId) {
        Membership caller = tenants.requireRole(organizationId, callerId, Roles.ADMIN);
        Membership target = findMember(organizationId, membershipId);
        requireCeiling(caller, target);
        return target;
    }

    /** The membership, only if it belongs to the organization. Other features resolve a member through this. */
    public Membership findMember(UUID organizationId, UUID membershipId) {
        return memberships.findByIdAndOrganizationId(membershipId, organizationId)
                .orElseThrow(MemberNotFoundException::new);
    }

    /** The member's current role must not be above the caller's. Equal is allowed. */
    private void requireCeiling(Membership caller, Membership target) {
        if (!tenants.hasAtLeast(caller, target.getRole().getName())) {
            throw new InsufficientOrganizationRoleException();
        }
    }

    /** Called when an ADMIN is about to lose the role or leave: another ADMIN must remain; the admin rows are locked, so two admins leaving at once cannot both pass. */
    private void requireAnotherAdmin(UUID organizationId) {
        if (memberships.findByOrganizationIdAndRoleName(organizationId, Roles.ADMIN).size() <= 1) {
            throw new LastAdminException();
        }
    }
}
