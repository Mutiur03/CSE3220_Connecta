package com.connecta.backend.org.service;

import com.connecta.backend.auth.dto.TokenResponse;
import com.connecta.backend.auth.service.AuthService;
import com.connecta.backend.auth.service.EmailAlreadyUsedException;
import com.connecta.backend.org.domain.Invitation;
import com.connecta.backend.org.dto.AcceptInvitationRequest;
import com.connecta.backend.org.dto.AcceptNewInvitationRequest;
import com.connecta.backend.org.dto.InvitationPreviewResponse;
import com.connecta.backend.org.repository.InvitationRepository;
import com.connecta.backend.org.repository.MembershipRepository;
import com.connecta.backend.org.domain.Membership;
import com.connecta.backend.user.domain.User;
import com.connecta.backend.user.service.UserService;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The invitation flow for someone who is not signed in. The token is the proof: it identifies the invitation,
 * and the invitation fixes the email and the role. Unknown, expired, revoked and accepted tokens all read as
 * one 404 on preview, so a visitor learns nothing about them.
 */
@Service
@RequiredArgsConstructor
public class InvitationSignupService {

    private final InvitationRepository invitations;
    private final MembershipRepository memberships;
    private final UserService users;
    private final AuthService auth;
    private final PasswordEncoder passwordEncoder;
    private final InvitationTokenGenerator tokens;
    private final Clock clock;

    /** Public. Only a pending, unexpired invitation can be previewed. */
    @Transactional(readOnly = true)
    public InvitationPreviewResponse preview(AcceptInvitationRequest request) {
        Invitation invitation = find(request.token());
        if (invitation.getStatus() != Invitation.Status.PENDING || invitation.isExpired(clock.instant())) {
            throw new InvitationNotFoundException();
        }
        return InvitationPreviewResponse.from(invitation, users.emailExists(invitation.getEmail()));
    }

    /**
     * Public. Creates the account for the invited email, with the invited role as its global role, joins the
     * organization and signs the new user in, all in one transaction. A failure leaves the invitation pending.
     * An email that already has an account must sign in and use the normal accept.
     */
    @Transactional
    public TokenResponse acceptAndSignUp(AcceptNewInvitationRequest request) {
        Invitation invitation = find(request.token());
        invitation.accept(clock);
        if (users.emailExists(invitation.getEmail())) {
            throw new EmailAlreadyUsedException();
        }
        try {
            User user = users.create(invitation.getRole().getName(), invitation.getEmail(),
                    passwordEncoder.encode(request.password()), request.fullName());
            memberships.saveAndFlush(new Membership(invitation.getOrganization(), user, invitation.getRole()));
            return auth.startSession(user);
        } catch (DataIntegrityViolationException e) {
            // A concurrent signup for the same email surfaces here, as a 409, and not at commit.
            throw new EmailAlreadyUsedException();
        }
    }

    private Invitation find(String rawToken) {
        return invitations.findByTokenHash(tokens.hash(rawToken)).orElseThrow(InvitationNotFoundException::new);
    }
}
