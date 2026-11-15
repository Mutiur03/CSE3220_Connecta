package com.connecta.backend.org.service;

import com.connecta.backend.auth.service.AccountNotAvailableException;
import com.connecta.backend.common.dto.PageResponse;
import com.connecta.backend.org.domain.Membership;
import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.org.dto.CreateOrganizationRequest;
import com.connecta.backend.org.dto.OrganizationResponse;
import com.connecta.backend.org.dto.UpdateOrganizationRequest;
import com.connecta.backend.org.repository.MembershipRepository;
import com.connecta.backend.org.repository.OrganizationRepository;
import com.connecta.backend.user.domain.Role;
import com.connecta.backend.user.domain.Roles;
import com.connecta.backend.user.domain.User;
import com.connecta.backend.user.service.UserService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Organizations and the caller's role in each. The role rules live here, because they depend on the
 * caller's membership of one organization, not on a global role. A caller who is not a member gets
 * the same 404 as an unknown id.
 */
@Service
@RequiredArgsConstructor
public class OrganizationService {

    /** Stable order for lists: by name, then by membership id so equal names never swap places. */
    private static final Sort BY_NAME = Sort.by("organization.name").ascending().and(Sort.by("id"));

    private final OrganizationRepository organizations;
    private final MembershipRepository memberships;
    private final UserService users;

    /** Any signed-in user. The creator becomes the OWNER. */
    @Transactional
    public OrganizationResponse create(String subject, CreateOrganizationRequest request) {
        User creator = users.findEnabledById(parseUserId(subject)).orElseThrow(AccountNotAvailableException::new);
        Role owner = users.findRole(Roles.OWNER);
        if (organizations.existsBySlug(request.slug())) {
            throw new OrganizationSlugTakenException();
        }
        try {
            Organization organization = organizations.saveAndFlush(new Organization(request.name(), request.slug()));
            memberships.save(new Membership(organization, creator, owner));
            return OrganizationResponse.from(organization);
        } catch (DataIntegrityViolationException e) {
            // A concurrent create with the same slug surfaces here, as a 409, and not at commit.
            throw new OrganizationSlugTakenException();
        }
    }

    /** Any member, whatever their role. */
    @Transactional(readOnly = true)
    public PageResponse<OrganizationResponse> listForCaller(String subject, int page, int size) {
        Page<Membership> found = memberships.findByUserId(parseUserId(subject), PageRequest.of(page, size, BY_NAME));
        return PageResponse.from(found, membership -> OrganizationResponse.from(membership.getOrganization()));
    }

    /** Any member, whatever their role. */
    @Transactional(readOnly = true)
    public OrganizationResponse get(String subject, UUID organizationId) {
        return OrganizationResponse.from(memberOf(organizationId, subject).getOrganization());
    }

    /** OWNER or ADMIN of the organization. The slug never changes. */
    @Transactional
    public OrganizationResponse rename(String subject, UUID organizationId, UpdateOrganizationRequest request) {
        Membership membership = memberOf(organizationId, subject);
        requireRole(membership, Roles.OWNER, Roles.ADMIN);
        Organization organization = membership.getOrganization();
        organization.rename(request.name());
        return OrganizationResponse.from(organization);
    }

    /**
     * OWNER of the organization only. The caller's membership goes first, because it still references the
     * organization inside this transaction; the other members' rows go with it (ON DELETE CASCADE).
     */
    @Transactional
    public void delete(String subject, UUID organizationId) {
        Membership membership = memberOf(organizationId, subject);
        requireRole(membership, Roles.OWNER);
        memberships.delete(membership);
        organizations.delete(membership.getOrganization());
    }

    private Membership memberOf(UUID organizationId, String subject) {
        return memberships.findByOrganizationIdAndUserId(organizationId, parseUserId(subject))
                .orElseThrow(() -> new OrganizationNotFoundException(organizationId));
    }

    private static void requireRole(Membership membership, String... allowedRoles) {
        if (!List.of(allowedRoles).contains(membership.getRole().getName())) {
            throw new InsufficientOrganizationRoleException();
        }
    }

    private static UUID parseUserId(String subject) {
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new AccountNotAvailableException();
        }
    }
}
