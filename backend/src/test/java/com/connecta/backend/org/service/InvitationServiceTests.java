package com.connecta.backend.org.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.connecta.backend.auth.service.AccountNotAvailableException;
import com.connecta.backend.common.dto.PageResponse;
import com.connecta.backend.config.InvitationProperties;
import com.connecta.backend.org.domain.Invitation;
import com.connecta.backend.org.domain.InvitationAlreadyAcceptedException;
import com.connecta.backend.org.domain.InvitationNotAcceptableException;
import com.connecta.backend.org.domain.Membership;
import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.org.dto.AcceptInvitationRequest;
import com.connecta.backend.org.dto.CreateInvitationRequest;
import com.connecta.backend.org.dto.InvitationCreatedResponse;
import com.connecta.backend.org.dto.InvitationResponse;
import com.connecta.backend.org.dto.OrganizationResponse;
import com.connecta.backend.org.repository.InvitationRepository;
import com.connecta.backend.org.repository.MembershipRepository;
import com.connecta.backend.support.TestClock;
import com.connecta.backend.support.TestUsers;
import com.connecta.backend.user.domain.Role;
import com.connecta.backend.user.domain.Roles;
import com.connecta.backend.user.domain.User;
import com.connecta.backend.user.service.UserService;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/** Built by hand rather than injected: Mockito would pass null for the clock and the properties. */
@ExtendWith(MockitoExtension.class)
class InvitationServiceTests {

    private static final String EMAIL = "jane@example.com";
    private static final String RAW_TOKEN = "raw-token";
    private static final String TOKEN_HASH = "h".repeat(64);
    private static final Duration TTL = Duration.ofDays(7);

    @Mock
    InvitationRepository invitations;

    @Mock
    MembershipRepository memberships;

    @Mock
    UserService users;

    @Mock
    TenantAccessService tenants;

    @Mock
    InvitationTokenGenerator tokens;

    @Mock
    ApplicationEventPublisher events;

    InvitationService service;

    private final UUID organizationId = UUID.randomUUID();
    private final Organization organization = organization();
    private final User admin = TestUsers.user("admin@example.com", Roles.ADMIN);
    private final String subject = admin.getId().toString();

    @BeforeEach
    void setUp() {
        service = new InvitationService(invitations, memberships, users, tenants, new ActiveCaller(users), tokens,
                new InvitationProperties(TTL), TestClock.FIXED, events);
        lenient().when(users.findEnabledById(admin.getId())).thenReturn(Optional.of(admin));
    }

    private Organization organization() {
        Organization org = new Organization("Acme", "acme");
        TestUsers.set(org, "id", organizationId);
        return org;
    }

    private Membership membership(User user, String roleName) {
        return new Membership(organization, user, new Role(roleName));
    }

    private Invitation invitation(String email) {
        Invitation invitation = new Invitation(organization, email, new Role(Roles.AGENT), admin, TOKEN_HASH,
                TestClock.NOW, TestClock.NOW.plus(TTL));
        TestUsers.set(invitation, "id", UUID.randomUUID());
        return invitation;
    }

    private void callerHoldsRole(String roleName) {
        when(tenants.requireRole(organizationId, admin.getId(), Roles.ADMIN))
                .thenReturn(membership(admin, roleName));
    }

    private static CreateInvitationRequest request(String email, String role) {
        return new CreateInvitationRequest(email, role);
    }

    @Nested
    class Create {

        @Test
        void anAdminInvitesAnEmailAndGetsTheRawTokenOnceWithoutTheHash() {
            callerHoldsRole(Roles.ADMIN);
            when(tenants.hasAtLeast(any(Membership.class), any(String.class))).thenReturn(true);
            when(users.findByEmail(EMAIL)).thenReturn(Optional.empty());
            when(invitations.existsByOrganizationIdAndEmailAndStatus(
                    organizationId, EMAIL, Invitation.Status.PENDING)).thenReturn(false);
            when(tokens.issue()).thenReturn(new InvitationTokenGenerator.Issued(RAW_TOKEN, TOKEN_HASH));
            when(users.findRole(Roles.AGENT)).thenReturn(new Role(Roles.AGENT));
            when(invitations.saveAndFlush(any(Invitation.class))).thenAnswer(call -> {
                Invitation saved = call.getArgument(0);
                TestUsers.set(saved, "id", UUID.randomUUID());
                return saved;
            });

            InvitationCreatedResponse created = service.create(subject, organizationId,
                    request("Jane@Example.com", Roles.AGENT));

            assertEquals(RAW_TOKEN, created.token());
            assertEquals(EMAIL, created.invitation().email());
            assertEquals(Roles.AGENT, created.invitation().role());
            assertEquals(Invitation.Status.PENDING.name(), created.invitation().status());
            ArgumentCaptor<Invitation> saved = ArgumentCaptor.forClass(Invitation.class);
            verify(invitations).saveAndFlush(saved.capture());
            assertEquals(TOKEN_HASH, saved.getValue().getTokenHash());
            assertEquals(TestClock.NOW.plus(TTL), saved.getValue().getExpiresAt());
        }

        @Test
        void publishesAnInvitationCreatedEventWithoutTheToken() {
            callerHoldsRole(Roles.ADMIN);
            when(tenants.hasAtLeast(any(Membership.class), any(String.class))).thenReturn(true);
            when(tokens.issue()).thenReturn(new InvitationTokenGenerator.Issued(RAW_TOKEN, TOKEN_HASH));
            when(users.findRole(Roles.AGENT)).thenReturn(new Role(Roles.AGENT));
            when(invitations.saveAndFlush(any(Invitation.class))).thenAnswer(call -> {
                Invitation saved = call.getArgument(0);
                TestUsers.set(saved, "id", UUID.randomUUID());
                return saved;
            });

            service.create(subject, organizationId, request(EMAIL, Roles.AGENT));

            ArgumentCaptor<InvitationCreatedEvent> event = ArgumentCaptor.forClass(InvitationCreatedEvent.class);
            verify(events).publishEvent(event.capture());
            assertEquals(organizationId, event.getValue().organizationId());
            assertEquals(EMAIL, event.getValue().email());
            assertEquals(Roles.AGENT, event.getValue().role());
            assertNotNull(event.getValue().invitationId());
        }

        @Test
        void aRoleAboveTheCallersIsRefusedBeforeAnyTokenIsMade() {
            callerHoldsRole(Roles.ADMIN);
            when(tenants.hasAtLeast(any(Membership.class), any(String.class))).thenReturn(false);

            assertThrows(InsufficientOrganizationRoleException.class,
                    () -> service.create(subject, organizationId, request(EMAIL, Roles.OWNER)));
            verify(tokens, never()).issue();
            verify(invitations, never()).saveAndFlush(any());
        }

        @Test
        void anEmailThatIsAlreadyAMemberIsRefused() {
            callerHoldsRole(Roles.ADMIN);
            when(tenants.hasAtLeast(any(Membership.class), any(String.class))).thenReturn(true);
            User existing = TestUsers.user(EMAIL, Roles.AGENT);
            when(users.findByEmail(EMAIL)).thenReturn(Optional.of(existing));
            when(tenants.isMember(organizationId, existing.getId())).thenReturn(true);

            assertThrows(AlreadyMemberException.class,
                    () -> service.create(subject, organizationId, request(EMAIL, Roles.AGENT)));
            verify(invitations, never()).saveAndFlush(any());
        }

        @Test
        void aSecondPendingInvitationForTheSameEmailIsRefused() {
            callerHoldsRole(Roles.ADMIN);
            when(tenants.hasAtLeast(any(Membership.class), any(String.class))).thenReturn(true);
            when(users.findByEmail(EMAIL)).thenReturn(Optional.empty());
            when(invitations.existsByOrganizationIdAndEmailAndStatus(
                    organizationId, EMAIL, Invitation.Status.PENDING)).thenReturn(true);

            assertThrows(DuplicateInvitationException.class,
                    () -> service.create(subject, organizationId, request(EMAIL, Roles.AGENT)));
            verify(tokens, never()).issue();
        }

        @Test
        void aConcurrentDuplicateCaughtByTheDatabaseIndexIsRefused() {
            callerHoldsRole(Roles.ADMIN);
            when(tenants.hasAtLeast(any(Membership.class), any(String.class))).thenReturn(true);
            when(tokens.issue()).thenReturn(new InvitationTokenGenerator.Issued(RAW_TOKEN, TOKEN_HASH));
            when(users.findRole(Roles.AGENT)).thenReturn(new Role(Roles.AGENT));
            when(invitations.saveAndFlush(any(Invitation.class)))
                    .thenThrow(new DataIntegrityViolationException("one pending per email"));

            assertThrows(DuplicateInvitationException.class,
                    () -> service.create(subject, organizationId, request(EMAIL, Roles.AGENT)));
            verify(events, never()).publishEvent(any());
        }

        @Test
        void aNonMemberCallerGetsNotAMember() {
            when(tenants.requireRole(organizationId, admin.getId(), Roles.ADMIN))
                    .thenThrow(new NotAMemberException());

            assertThrows(NotAMemberException.class,
                    () -> service.create(subject, organizationId, request(EMAIL, Roles.AGENT)));
        }

        @Test
        void aDisabledOrMissingAccountIsNotAvailable() {
            when(users.findEnabledById(admin.getId())).thenReturn(Optional.empty());

            assertThrows(AccountNotAvailableException.class,
                    () -> service.create(subject, organizationId, request(EMAIL, Roles.AGENT)));
        }
    }

    @Nested
    class ListForAdmins {

        @Test
        void returnsOneNewestFirstPageOfTheOrganizationsInvitations() {
            callerHoldsRole(Roles.ADMIN);
            Invitation pending = invitation(EMAIL);
            when(invitations.findByOrganizationId(any(UUID.class), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(pending), PageRequest.of(0, 20), 1));

            PageResponse<InvitationResponse> page = service.list(subject, organizationId, 0, 20);

            assertEquals(1, page.totalElements());
            assertEquals(EMAIL, page.content().get(0).email());
            verify(invitations).findByOrganizationId(any(UUID.class), any(Pageable.class));
        }

        @Test
        void anAgentIsRefusedBeforeTheListIsRead() {
            when(tenants.requireRole(organizationId, admin.getId(), Roles.ADMIN))
                    .thenThrow(new InsufficientOrganizationRoleException());

            assertThrows(InsufficientOrganizationRoleException.class,
                    () -> service.list(subject, organizationId, 0, 20));
            verify(invitations, never()).findByOrganizationId(any(UUID.class), any(Pageable.class));
        }
    }

    @Nested
    class Revoke {

        @Test
        void aPendingInvitationIsRevoked() {
            callerHoldsRole(Roles.ADMIN);
            Invitation pending = invitation(EMAIL);
            UUID invitationId = pending.getId();
            when(invitations.findByIdAndOrganizationId(invitationId, organizationId))
                    .thenReturn(Optional.of(pending));

            service.revoke(subject, organizationId, invitationId);

            assertEquals(Invitation.Status.REVOKED, pending.getStatus());
        }

        @Test
        void anUnknownOrOtherOrganizationsInvitationIsNotFound() {
            callerHoldsRole(Roles.ADMIN);
            UUID invitationId = UUID.randomUUID();
            when(invitations.findByIdAndOrganizationId(invitationId, organizationId)).thenReturn(Optional.empty());

            assertThrows(InvitationNotFoundException.class,
                    () -> service.revoke(subject, organizationId, invitationId));
        }

        @Test
        void anAcceptedInvitationCannotBeRevoked() {
            callerHoldsRole(Roles.ADMIN);
            Invitation accepted = invitation(EMAIL);
            accepted.accept(TestClock.FIXED);
            when(invitations.findByIdAndOrganizationId(accepted.getId(), organizationId))
                    .thenReturn(Optional.of(accepted));

            assertThrows(InvitationAlreadyAcceptedException.class,
                    () -> service.revoke(subject, organizationId, accepted.getId()));
        }
    }

    @Nested
    class Accept {

        private final User invitee = TestUsers.user(EMAIL, Roles.AGENT);
        private final String inviteeSubject = invitee.getId().toString();

        @BeforeEach
        void inviteeIsEnabled() {
            lenient().when(users.findEnabledById(invitee.getId())).thenReturn(Optional.of(invitee));
            lenient().when(tokens.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
        }

        private void membershipSavesAsGiven() {
            when(memberships.saveAndFlush(any(Membership.class))).thenAnswer(call -> call.getArgument(0));
        }

        @Test
        void theMatchingAccountJoinsWithTheInvitedRole() {
            Invitation invitation = invitation(EMAIL);
            when(invitations.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(invitation));
            when(tenants.isMember(organizationId, invitee.getId())).thenReturn(false);
            membershipSavesAsGiven();

            OrganizationResponse joined = service.accept(inviteeSubject, new AcceptInvitationRequest(RAW_TOKEN));

            assertEquals("Acme", joined.name());
            assertEquals(Roles.AGENT, joined.callerRole());
            assertEquals(Invitation.Status.ACCEPTED, invitation.getStatus());
            assertEquals(TestClock.NOW, invitation.getAcceptedAt());
            ArgumentCaptor<Membership> membership = ArgumentCaptor.forClass(Membership.class);
            verify(memberships).saveAndFlush(membership.capture());
            assertEquals(invitee, membership.getValue().getUser());
        }

        @Test
        void anUnknownTokenIsNotFound() {
            when(invitations.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.empty());

            assertThrows(InvitationNotFoundException.class,
                    () -> service.accept(inviteeSubject, new AcceptInvitationRequest(RAW_TOKEN)));
        }

        @Test
        void aTokenAddressedToAnotherEmailIsTheSameNotFound() {
            Invitation forSomeoneElse = invitation("someone.else@example.com");
            when(invitations.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(forSomeoneElse));

            assertThrows(InvitationNotFoundException.class,
                    () -> service.accept(inviteeSubject, new AcceptInvitationRequest(RAW_TOKEN)));
            assertEquals(Invitation.Status.PENDING, forSomeoneElse.getStatus());
        }

        @Test
        void anAccountThatIsAlreadyAMemberIsRefusedAndTheInvitationStaysPending() {
            Invitation invitation = invitation(EMAIL);
            when(invitations.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(invitation));
            when(tenants.isMember(organizationId, invitee.getId())).thenReturn(true);

            assertThrows(AlreadyMemberException.class,
                    () -> service.accept(inviteeSubject, new AcceptInvitationRequest(RAW_TOKEN)));
            assertEquals(Invitation.Status.PENDING, invitation.getStatus());
            verify(memberships, never()).saveAndFlush(any());
        }

        @Test
        void anExpiredInvitationIsNotAcceptable() {
            Invitation expired = new Invitation(organization, EMAIL, new Role(Roles.AGENT), admin, TOKEN_HASH,
                    TestClock.NOW.minus(TTL).minusSeconds(1), TestClock.NOW.minusSeconds(1));
            when(invitations.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(expired));
            when(tenants.isMember(organizationId, invitee.getId())).thenReturn(false);

            assertThrows(InvitationNotAcceptableException.class,
                    () -> service.accept(inviteeSubject, new AcceptInvitationRequest(RAW_TOKEN)));
            verify(memberships, never()).saveAndFlush(any());
        }

        @Test
        void aRevokedInvitationIsNotAcceptable() {
            Invitation invitation = invitation(EMAIL);
            invitation.revoke();
            when(invitations.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(invitation));
            when(tenants.isMember(organizationId, invitee.getId())).thenReturn(false);

            assertThrows(InvitationNotAcceptableException.class,
                    () -> service.accept(inviteeSubject, new AcceptInvitationRequest(RAW_TOKEN)));
        }

        @Test
        void anAlreadyAcceptedInvitationIsRefusedForAnotherAttempt() {
            Invitation invitation = invitation(EMAIL);
            invitation.accept(TestClock.FIXED);
            when(invitations.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(invitation));
            when(tenants.isMember(organizationId, invitee.getId())).thenReturn(false);

            assertThrows(InvitationAlreadyAcceptedException.class,
                    () -> service.accept(inviteeSubject, new AcceptInvitationRequest(RAW_TOKEN)));
        }

        @Test
        void aConcurrentAcceptThatHitsTheMembershipKeyIsAlreadyAMember() {
            Invitation invitation = invitation(EMAIL);
            when(invitations.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(invitation));
            when(tenants.isMember(organizationId, invitee.getId())).thenReturn(false);
            when(memberships.saveAndFlush(any(Membership.class)))
                    .thenThrow(new DataIntegrityViolationException("memberships_organization_user_key"));

            assertThrows(AlreadyMemberException.class,
                    () -> service.accept(inviteeSubject, new AcceptInvitationRequest(RAW_TOKEN)));
        }

        @Test
        void aDisabledAccountCannotAccept() {
            when(users.findEnabledById(invitee.getId())).thenReturn(Optional.empty());

            assertThrows(AccountNotAvailableException.class,
                    () -> service.accept(inviteeSubject, new AcceptInvitationRequest(RAW_TOKEN)));
            verify(invitations, never()).findByTokenHash(any());
        }
    }
}
