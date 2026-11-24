package com.connecta.backend.org.service;

import com.connecta.backend.common.dto.PageResponse;
import com.connecta.backend.org.domain.Membership;
import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.org.dto.CreateOrganizationRequest;
import com.connecta.backend.org.dto.OrganizationResponse;
import com.connecta.backend.org.dto.OrganizationSummaryResponse;
import com.connecta.backend.org.dto.UpdateOrganizationRequest;
import com.connecta.backend.org.repository.MembershipRepository;
import com.connecta.backend.org.repository.OrganizationRepository;
import com.connecta.backend.user.domain.Role;
import com.connecta.backend.user.domain.Roles;
import com.connecta.backend.user.domain.User;
import com.connecta.backend.user.service.UserService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Organization lifecycle. Membership and role checks belong to {@link TenantAccessService}; this service
 * only chooses which role each operation requires. Every operation first confirms the caller's account is
 * still enabled, so a disabled user with an unexpired token is refused.
 */
@Service
@RequiredArgsConstructor
public class OrganizationService {

    /** Stable order for lists: by name, then by membership id so equal names never swap places. */
    private static final Sort BY_NAME = Sort.by("organization.name").ascending().and(Sort.by("id"));

    private final OrganizationRepository organizations;
    private final MembershipRepository memberships;
    private final UserService users;
    private final TenantAccessService tenants;
    private final ActiveCaller activeCaller;
    private final ApplicationEventPublisher events;

    /** Any signed-in user. The creator becomes the organization's ADMIN. */
    @Transactional
    public OrganizationResponse create(String subject, CreateOrganizationRequest request) {
        User creator = activeCaller.load(subject);
        Role admin = users.findRole(Roles.ADMIN);
        if (organizations.existsBySlug(request.slug())) {
            throw new OrganizationSlugTakenException();
        }
        try {
            Organization organization = organizations.saveAndFlush(new Organization(request.name(), request.slug()));
            return OrganizationResponse.from(memberships.save(new Membership(organization, creator, admin)));
        } catch (DataIntegrityViolationException e) {
            // A concurrent create with the same slug surfaces here, as a 409, and not at commit.
            throw new OrganizationSlugTakenException();
        }
    }

    /** The system OWNER only: every organization, with its member count, whoever created it. */
    @PreAuthorize("hasRole('" + Roles.OWNER + "')")
    @Transactional(readOnly = true)
    public PageResponse<OrganizationSummaryResponse> listAll(int page, int size) {
        Sort byName = Sort.by("name").ascending().and(Sort.by("id"));
        return PageResponse.from(organizations.findAllSummaries(PageRequest.of(page, size, byName)),
                OrganizationSummaryResponse::from);
    }

    /** Any member, whatever their role. */
    @Transactional(readOnly = true)
    public PageResponse<OrganizationResponse> listForCaller(String subject, int page, int size) {
        UUID callerId = activeCaller.load(subject).getId();
        Page<Membership> found = memberships.findByUserId(callerId, PageRequest.of(page, size, BY_NAME));
        return PageResponse.from(found, OrganizationResponse::from);
    }

    /** Any member, whatever their role. */
    @Transactional(readOnly = true)
    public OrganizationResponse get(String subject, UUID organizationId) {
        UUID callerId = activeCaller.load(subject).getId();
        return OrganizationResponse.from(tenants.requireMembership(organizationId, callerId));
    }

    /** ADMIN of the organization. */
    @Transactional
    public OrganizationResponse rename(String subject, UUID organizationId, UpdateOrganizationRequest request) {
        Membership membership = tenants.requireRole(organizationId, activeCaller.load(subject).getId(), Roles.ADMIN);
        membership.getOrganization().rename(request.name());
        return OrganizationResponse.from(membership);
    }

    /**
     * ADMIN of the organization. The caller's membership goes first, because it still references the
     * organization inside this transaction; the other members' rows go with it (ON DELETE CASCADE).
     */
    @Transactional
    public void delete(String subject, UUID organizationId) {
        Membership membership = tenants.requireRole(organizationId, activeCaller.load(subject).getId(), Roles.ADMIN);
        memberships.delete(membership);
        organizations.delete(membership.getOrganization());
        events.publishEvent(new OrganizationDeletedEvent(organizationId));
    }
}
