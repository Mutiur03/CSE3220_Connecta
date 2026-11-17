package com.connecta.backend.org.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.connecta.backend.auth.dto.TokenResponse;
import com.connecta.backend.auth.service.AuthService;
import com.connecta.backend.auth.service.EmailAlreadyUsedException;
import com.connecta.backend.org.domain.Invitation;
import com.connecta.backend.org.domain.InvitationAlreadyAcceptedException;
import com.connecta.backend.org.domain.InvitationNotAcceptableException;
import com.connecta.backend.org.domain.Membership;
import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.org.dto.AcceptInvitationRequest;
import com.connecta.backend.org.dto.AcceptNewInvitationRequest;
import com.connecta.backend.org.dto.InvitationPreviewResponse;
import com.connecta.backend.org.repository.InvitationRepository;
import com.connecta.backend.org.repository.MembershipRepository;
import com.connecta.backend.support.TestClock;
import com.connecta.backend.support.TestUsers;
import com.connecta.backend.user.domain.Role;
import com.connecta.backend.user.domain.Roles;
import com.connecta.backend.user.domain.User;
import com.connecta.backend.user.service.UserService;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Built by hand so the real token hasher, the real encoder and the fixed clock run under the rules being tested. */
@ExtendWith(MockitoExtension.class)
class InvitationSignupServiceTests {

    private static final String EMAIL = "newbie@example.com";
    private static final String RAW_TOKEN = "raw-token";

    @Mock
    InvitationRepository invitations;

    @Mock
    MembershipRepository memberships;

    @Mock
    UserService users;

    @Mock
    AuthService auth;

    InvitationTokenGenerator tokens = new InvitationTokenGenerator();
    BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    InvitationSignupService service;

    private final Organization organization = new Organization("Acme", "acme");
    private final User inviter = TestUsers.user("admin@example.com", Roles.ADMIN);

    @BeforeEach
    void setUp() {
        service = new InvitationSignupService(invitations, memberships, users, auth, encoder, tokens,
                TestClock.FIXED);
        TestUsers.set(organization, "id", UUID.randomUUID());
    }

    private Invitation invitation(String roleName, Instant expiresAt) {
        Invitation invitation = new Invitation(organization, EMAIL, new Role(roleName), inviter,
                tokens.hash(RAW_TOKEN), TestClock.NOW.minus(Duration.ofDays(1)), expiresAt);
        TestUsers.set(invitation, "id", UUID.randomUUID());
        when(invitations.findByTokenHash(tokens.hash(RAW_TOKEN))).thenReturn(Optional.of(invitation));
        return invitation;
    }

    private Invitation pending(String roleName) {
        return invitation(roleName, TestClock.NOW.plus(Duration.ofDays(6)));
    }

    private static AcceptInvitationRequest preview() {
        return new AcceptInvitationRequest(RAW_TOKEN);
    }

    private static AcceptNewInvitationRequest signup() {
        return new AcceptNewInvitationRequest(RAW_TOKEN, "Newbie", "correct-password");
    }

    @Nested
    class Preview {

        @Test
        void showsTheInvitationAndThatNoAccountExistsYet() {
            pending(Roles.AGENT);
            when(users.emailExists(EMAIL)).thenReturn(false);

            InvitationPreviewResponse response = service.preview(preview());

            assertEquals(EMAIL, response.email());
            assertEquals("Acme", response.organizationName());
            assertEquals(Roles.AGENT, response.role());
            assertEquals(inviter.getFullName(), response.inviterName());
            assertFalse(response.accountExists());
        }

        @Test
        void tellsThePageWhenTheEmailAlreadyHasAnAccount() {
            pending(Roles.ADMIN);
            when(users.emailExists(EMAIL)).thenReturn(true);

            assertTrue(service.preview(preview()).accountExists());
        }

        @Test
        void anUnknownTokenIsNotFound() {
            when(invitations.findByTokenHash(any())).thenReturn(Optional.empty());

            assertThrows(InvitationNotFoundException.class, () -> service.preview(preview()));
        }

        @Test
        void anExpiredInvitationReadsAsNotFound() {
            invitation(Roles.AGENT, TestClock.NOW.minus(Duration.ofMinutes(1)));

            assertThrows(InvitationNotFoundException.class, () -> service.preview(preview()));
        }

        @Test
        void aRevokedInvitationReadsAsNotFound() {
            pending(Roles.AGENT).revoke();

            assertThrows(InvitationNotFoundException.class, () -> service.preview(preview()));
        }

        @Test
        void anAcceptedInvitationReadsAsNotFound() {
            pending(Roles.AGENT).accept(TestClock.FIXED);

            assertThrows(InvitationNotFoundException.class, () -> service.preview(preview()));
        }
    }

    @Nested
    class AcceptAndSignUp {

        @Test
        void createsTheAccountWithTheInvitedRoleJoinsAndSignsIn() {
            Invitation invitation = pending(Roles.AGENT);
            when(users.emailExists(EMAIL)).thenReturn(false);
            User created = TestUsers.user(EMAIL, Roles.AGENT);
            when(users.create(eq(Roles.AGENT), eq(EMAIL), anyString(), eq("Newbie"))).thenReturn(created);
            TokenResponse session = new TokenResponse("Bearer", "access", 900, "refresh", null);
            when(auth.startSession(created)).thenReturn(session);

            TokenResponse response = service.acceptAndSignUp(signup());

            assertEquals(session, response);
            assertEquals(Invitation.Status.ACCEPTED, invitation.getStatus());
            ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
            verify(users).create(eq(Roles.AGENT), eq(EMAIL), hash.capture(), eq("Newbie"));
            assertNotEquals("correct-password", hash.getValue());
            assertTrue(encoder.matches("correct-password", hash.getValue()));
            ArgumentCaptor<Membership> membership = ArgumentCaptor.forClass(Membership.class);
            verify(memberships).saveAndFlush(membership.capture());
            assertEquals(created, membership.getValue().getUser());
            assertEquals(Roles.AGENT, membership.getValue().getRole().getName());
        }

        @Test
        void anInvitedAdminGetsTheAdminRole() {
            pending(Roles.ADMIN);
            when(users.emailExists(EMAIL)).thenReturn(false);
            User created = TestUsers.user(EMAIL, Roles.ADMIN);
            when(users.create(eq(Roles.ADMIN), anyString(), anyString(), anyString())).thenReturn(created);

            service.acceptAndSignUp(signup());

            verify(users).create(eq(Roles.ADMIN), eq(EMAIL), anyString(), eq("Newbie"));
        }

        @Test
        void anEmailThatAlreadyHasAnAccountMustSignInInstead() {
            pending(Roles.AGENT);
            when(users.emailExists(EMAIL)).thenReturn(true);

            assertThrows(EmailAlreadyUsedException.class, () -> service.acceptAndSignUp(signup()));
            verify(users, never()).create(any(), any(), any(), any());
            verify(memberships, never()).saveAndFlush(any());
        }

        @Test
        void aConcurrentSignupForTheSameEmailIsReportedAsTaken() {
            pending(Roles.AGENT);
            when(users.emailExists(EMAIL)).thenReturn(false);
            when(users.create(anyString(), anyString(), anyString(), anyString()))
                    .thenThrow(new DataIntegrityViolationException("dup"));

            assertThrows(EmailAlreadyUsedException.class, () -> service.acceptAndSignUp(signup()));
        }

        @Test
        void anUnknownTokenIsNotFound() {
            when(invitations.findByTokenHash(any())).thenReturn(Optional.empty());

            assertThrows(InvitationNotFoundException.class, () -> service.acceptAndSignUp(signup()));
        }

        @Test
        void anExpiredInvitationIsNotAcceptable() {
            invitation(Roles.AGENT, TestClock.NOW.minus(Duration.ofMinutes(1)));

            assertThrows(InvitationNotAcceptableException.class, () -> service.acceptAndSignUp(signup()));
            verify(users, never()).create(any(), any(), any(), any());
        }

        @Test
        void aRevokedInvitationIsNotAcceptable() {
            pending(Roles.AGENT).revoke();

            assertThrows(InvitationNotAcceptableException.class, () -> service.acceptAndSignUp(signup()));
        }

        @Test
        void anAcceptedInvitationIsAlreadyAccepted() {
            pending(Roles.AGENT).accept(TestClock.FIXED);

            assertThrows(InvitationAlreadyAcceptedException.class, () -> service.acceptAndSignUp(signup()));
            verify(users, never()).create(any(), any(), any(), any());
        }
    }
}
