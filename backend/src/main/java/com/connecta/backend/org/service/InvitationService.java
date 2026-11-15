package com.connecta.backend.org.service;

import com.connecta.backend.auth.service.AccountNotAvailableException;
import com.connecta.backend.common.dto.PageResponse;
import com.connecta.backend.config.InvitationProperties;
import com.connecta.backend.org.domain.Invitation;
import com.connecta.backend.org.domain.Membership;
import com.connecta.backend.org.dto.AcceptInvitationRequest;
import com.connecta.backend.org.dto.CreateInvitationRequest;
import com.connecta.backend.org.dto.InvitationCreatedResponse;
import com.connecta.backend.org.dto.InvitationResponse;
import com.connecta.backend.org.dto.OrganizationResponse;
import com.connecta.backend.org.repository.InvitationRepository;
import com.connecta.backend.org.repository.MembershipRepository;
import com.connecta.backend.user.domain.Role;
import com.connecta.backend.user.domain.Roles;
import com.connecta.backend.user.domain.User;
import com.connecta.backend.user.service.UserService;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Invitations to join an organization and their acceptance. Membership and role checks go through
 * {@link TenantAccessService}. An invitation is bound to one email: only the account with that email can
 * accept it, and anyone else gets the same 404 as for an unknown token.
 */
@Service
@RequiredArgsConstructor
public class InvitationService {

    /** Newest first, then by id so equal timestamps never swap places between pages. */
    private static final Sort NEWEST_FIRST = Sort.by("createdAt").descending().and(Sort.by("id"));

    private final InvitationRepository invitations;
    private final MembershipRepository memberships;
    private final UserService users;
    private final TenantAccessService tenants;
    private final InvitationTokenGenerator tokens;
    private final InvitationProperties props;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    /**
     * ADMIN or above. The invited role may not be higher than the caller's own role, so an ADMIN cannot
     * invite an OWNER. The raw token is returned here and nowhere else.
     */
    @Transactional
    public InvitationCreatedResponse create(String subject, UUID organizationId, CreateInvitationRequest request) {
        Membership inviter = tenants.requireRole(organizationId, activeCaller(subject).getId(), Roles.ADMIN);
        if (!tenants.hasAtLeast(inviter, request.role())) {
            throw new InsufficientOrganizationRoleException();
        }
        String email = User.normalizeEmail(request.email());
        rejectExistingMember(organizationId, email);
        if (invitations.existsByOrganizationIdAndEmailAndStatus(organizationId, email, Invitation.Status.PENDING)) {
            throw new DuplicateInvitationException();
        }

        InvitationTokenGenerator.Issued token = tokens.issue();
        Instant now = clock.instant();
        Invitation invitation = new Invitation(inviter.getOrganization(), email, users.findRole(request.role()),
                inviter.getUser(), token.hash(), now, now.plus(props.ttl()));
        try {
            invitation = invitations.saveAndFlush(invitation);
        } catch (DataIntegrityViolationException e) {
            // A concurrent invite for the same email hits the one-pending-per-email index.
            throw new DuplicateInvitationException();
        }
        events.publishEvent(new InvitationCreatedEvent(invitation.getId(), organizationId, email, request.role()));
        return new InvitationCreatedResponse(InvitationResponse.from(invitation), token.raw());
    }

    /** ADMIN or above. Pending, accepted and revoked invitations, newest first. */
    @Transactional(readOnly = true)
    public PageResponse<InvitationResponse> list(String subject, UUID organizationId, int page, int size) {
        tenants.requireRole(organizationId, activeCaller(subject).getId(), Roles.ADMIN);
        Page<Invitation> found =
                invitations.findByOrganizationId(organizationId, PageRequest.of(page, size, NEWEST_FIRST));
        return PageResponse.from(found, InvitationResponse::from);
    }

    /** ADMIN or above. Revoking an already revoked invitation does nothing; an accepted one is refused. */
    @Transactional
    public void revoke(String subject, UUID organizationId, UUID invitationId) {
        tenants.requireRole(organizationId, activeCaller(subject).getId(), Roles.ADMIN);
        Invitation invitation = invitations.findByIdAndOrganizationId(invitationId, organizationId)
                .orElseThrow(InvitationNotFoundException::new);
        invitation.revoke();
    }

    /**
     * Any signed-in account whose email matches the invitation. Creates the membership with the invited
     * role in the same transaction, so a failure leaves the invitation pending.
     */
    @Transactional
    public OrganizationResponse accept(String subject, AcceptInvitationRequest request) {
        User invitee = activeCaller(subject);
        Invitation invitation = invitations.findByTokenHash(tokens.hash(request.token()))
                .filter(found -> found.getEmail().equals(invitee.getEmail()))
                .orElseThrow(InvitationNotFoundException::new);
        UUID organizationId = invitation.getOrganization().getId();
        if (tenants.isMember(organizationId, invitee.getId())) {
            throw new AlreadyMemberException();
        }
        invitation.accept(clock);
        try {
            Membership membership = memberships.saveAndFlush(
                    new Membership(invitation.getOrganization(), invitee, invitation.getRole()));
            return OrganizationResponse.from(membership);
        } catch (DataIntegrityViolationException e) {
            // A concurrent accept of the same invitation creates the membership first.
            throw new AlreadyMemberException();
        }
    }

    private void rejectExistingMember(UUID organizationId, String email) {
        boolean alreadyMember = users.findByEmail(email)
                .filter(existing -> tenants.isMember(organizationId, existing.getId()))
                .isPresent();
        if (alreadyMember) {
            throw new AlreadyMemberException();
        }
    }

    /** The caller, once the account is confirmed to exist and be enabled. */
    private User activeCaller(String subject) {
        return users.findEnabledById(parseUserId(subject)).orElseThrow(AccountNotAvailableException::new);
    }

    private static UUID parseUserId(String subject) {
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new AccountNotAvailableException();
        }
    }
}
