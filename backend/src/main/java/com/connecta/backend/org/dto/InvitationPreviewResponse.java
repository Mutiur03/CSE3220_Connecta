package com.connecta.backend.org.dto;

import com.connecta.backend.org.domain.Invitation;

/**
 * What a visitor holding an invitation token may see before accepting. {@code accountExists} tells the page
 * whether to ask for a password (new account) or send the person to sign in.
 */
public record InvitationPreviewResponse(String email, String organizationName, String role, String inviterName,
        boolean accountExists) {

    public static InvitationPreviewResponse from(Invitation invitation, boolean accountExists) {
        return new InvitationPreviewResponse(invitation.getEmail(), invitation.getOrganization().getName(),
                invitation.getRole().getName(), invitation.getInvitedBy().getFullName(), accountExists);
    }
}
