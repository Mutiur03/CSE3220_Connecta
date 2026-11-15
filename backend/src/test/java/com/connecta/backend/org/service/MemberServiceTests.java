package com.connecta.backend.org.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.connecta.backend.auth.service.AccountNotAvailableException;
import com.connecta.backend.common.dto.PageResponse;
import com.connecta.backend.org.domain.Membership;
import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.org.dto.MemberResponse;
import com.connecta.backend.org.repository.MembershipRepository;
import com.connecta.backend.support.TestUsers;
import com.connecta.backend.user.domain.Role;
import com.connecta.backend.user.domain.Roles;
import com.connecta.backend.user.domain.User;
import com.connecta.backend.user.service.UserService;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;

/** Built by hand so the real TenantAccessService and its role order run under the rules being tested. */
@ExtendWith(MockitoExtension.class)
class MemberServiceTests {

    @Mock
    MembershipRepository memberships;

    @Mock
    UserService users;

    MemberService service;

    private final UUID organizationId = UUID.randomUUID();
    private final Organization organization = organization();
    private final User caller = TestUsers.user("caller@example.com", Roles.AGENT);
    private final String subject = caller.getId().toString();

    @BeforeEach
    void setUp() {
        TenantAccessService tenants = new TenantAccessService(memberships, RoleHierarchyImpl.fromHierarchy(
                "ROLE_OWNER > ROLE_ADMIN\nROLE_ADMIN > ROLE_AGENT"));
        service = new MemberService(memberships, users, tenants, new ActiveCaller(users));
        lenient().when(users.findEnabledById(caller.getId())).thenReturn(Optional.of(caller));
        lenient().when(users.findRole(anyString())).thenAnswer(call -> new Role(call.getArgument(0)));
    }

    private Organization organization() {
        Organization org = new Organization("Acme", "acme");
        TestUsers.set(org, "id", organizationId);
        return org;
    }

    private Membership membership(User user, String roleName) {
        Membership membership = new Membership(organization, user, new Role(roleName));
        TestUsers.set(membership, "id", UUID.randomUUID());
        return membership;
    }

    /** The caller holds this role in the organization. */
    private Membership callerIs(String roleName) {
        Membership mine = membership(caller, roleName);
        when(memberships.findByOrganizationIdAndUserId(organizationId, caller.getId())).thenReturn(Optional.of(mine));
        return mine;
    }

    /** A member with this role, found by its membership id inside the organization. */
    private Membership memberWith(String roleName) {
        Membership target = membership(TestUsers.user(roleName.toLowerCase() + "@example.com", roleName), roleName);
        when(memberships.findByIdAndOrganizationId(target.getId(), organizationId)).thenReturn(Optional.of(target));
        return target;
    }

    private void ownerCountIs(long count) {
        lenient().when(memberships.findByOrganizationIdAndRoleName(organizationId, Roles.OWNER)).thenReturn(
                java.util.Collections.nCopies((int) count, membership(TestUsers.user("o@example.com", Roles.OWNER), Roles.OWNER)));
    }

    @Nested
    class ListMembers {

        @Test
        void aMemberSeesNameEmailRoleAndJoinDateOfEachMember() {
            callerIs(Roles.AGENT);
            Membership member = membership(TestUsers.user("jane@example.com", Roles.ADMIN), Roles.ADMIN);
            when(memberships.findByOrganizationId(eq(organizationId), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(member), PageRequest.of(0, 20), 1));

            PageResponse<MemberResponse> page = service.list(subject, organizationId, 0, 20);

            assertEquals(1, page.totalElements());
            MemberResponse row = page.content().get(0);
            assertEquals(member.getId(), row.membershipId());
            assertEquals("jane@example.com", row.email());
            assertEquals(Roles.ADMIN, row.role());
            assertEquals(member.getCreatedAt(), row.joinedAt());
        }

        @Test
        void sortsByNameThenIdSoPagesStayStable() {
            callerIs(Roles.AGENT);
            when(memberships.findByOrganizationId(eq(organizationId), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

            service.list(subject, organizationId, 0, 20);

            ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
            verify(memberships).findByOrganizationId(eq(organizationId), pageable.capture());
            assertNotNull(pageable.getValue().getSort().getOrderFor("user.fullName"));
            assertNotNull(pageable.getValue().getSort().getOrderFor("id"));
        }

        @Test
        void aNonMemberGetsNotAMember() {
            when(memberships.findByOrganizationIdAndUserId(organizationId, caller.getId())).thenReturn(Optional.empty());

            assertThrows(NotAMemberException.class, () -> service.list(subject, organizationId, 0, 20));
        }

        @Test
        void aDisabledAccountIsNotAvailable() {
            when(users.findEnabledById(caller.getId())).thenReturn(Optional.empty());

            assertThrows(AccountNotAvailableException.class, () -> service.list(subject, organizationId, 0, 20));
        }
    }

    @Nested
    class ChangeRole {

        @Test
        void anOwnerPromotesAnAgentToAdmin() {
            callerIs(Roles.OWNER);
            Membership target = memberWith(Roles.AGENT);

            MemberResponse changed = service.changeRole(subject, organizationId, target.getId(), Roles.ADMIN);

            assertEquals(Roles.ADMIN, changed.role());
            assertEquals(Roles.ADMIN, target.getRole().getName());
        }

        @Test
        void anOwnerCanGrantOwnerToAnotherMember() {
            callerIs(Roles.OWNER);
            Membership target = memberWith(Roles.ADMIN);

            assertEquals(Roles.OWNER, service.changeRole(subject, organizationId, target.getId(), Roles.OWNER).role());
        }

        @Test
        void anAdminDemotesAnAdminToAgent() {
            callerIs(Roles.ADMIN);
            Membership target = memberWith(Roles.ADMIN);

            assertEquals(Roles.AGENT, service.changeRole(subject, organizationId, target.getId(), Roles.AGENT).role());
        }

        @Test
        void anAdminCannotGrantOwner() {
            callerIs(Roles.ADMIN);
            Membership target = memberWith(Roles.AGENT);

            assertThrows(InsufficientOrganizationRoleException.class,
                    () -> service.changeRole(subject, organizationId, target.getId(), Roles.OWNER));
            assertEquals(Roles.AGENT, target.getRole().getName());
        }

        @Test
        void anAdminCannotTouchAnOwner() {
            callerIs(Roles.ADMIN);
            Membership target = memberWith(Roles.OWNER);

            assertThrows(InsufficientOrganizationRoleException.class,
                    () -> service.changeRole(subject, organizationId, target.getId(), Roles.AGENT));
            verify(memberships, never()).findByOrganizationIdAndRoleName(any(), any());
        }

        @Test
        void anAgentCannotChangeRolesAndTheMemberIsNotLoaded() {
            callerIs(Roles.AGENT);

            assertThrows(InsufficientOrganizationRoleException.class,
                    () -> service.changeRole(subject, organizationId, UUID.randomUUID(), Roles.AGENT));
            verify(memberships, never()).findByIdAndOrganizationId(any(), any());
        }

        @Test
        void aNonMemberGetsNotAMember() {
            when(memberships.findByOrganizationIdAndUserId(organizationId, caller.getId())).thenReturn(Optional.empty());

            assertThrows(NotAMemberException.class,
                    () -> service.changeRole(subject, organizationId, UUID.randomUUID(), Roles.AGENT));
        }

        @Test
        void aMembershipOfAnotherOrganizationIsNotFound() {
            callerIs(Roles.OWNER);
            UUID otherOrganizationsMembership = UUID.randomUUID();
            when(memberships.findByIdAndOrganizationId(otherOrganizationsMembership, organizationId))
                    .thenReturn(Optional.empty());

            assertThrows(MemberNotFoundException.class,
                    () -> service.changeRole(subject, organizationId, otherOrganizationsMembership, Roles.ADMIN));
        }

        @Test
        void demotingTheLastOwnerIsRefused() {
            callerIs(Roles.OWNER);
            Membership target = memberWith(Roles.OWNER);
            ownerCountIs(1);

            assertThrows(LastOwnerException.class,
                    () -> service.changeRole(subject, organizationId, target.getId(), Roles.ADMIN));
            assertEquals(Roles.OWNER, target.getRole().getName());
        }

        @Test
        void demotingAnOwnerIsAllowedWhenAnotherOwnerRemains() {
            callerIs(Roles.OWNER);
            Membership target = memberWith(Roles.OWNER);
            ownerCountIs(2);

            assertEquals(Roles.ADMIN, service.changeRole(subject, organizationId, target.getId(), Roles.ADMIN).role());
        }

        @Test
        void anOwnerKeepingTheOwnerRoleNeverCountsOwners() {
            callerIs(Roles.OWNER);
            Membership target = memberWith(Roles.OWNER);

            service.changeRole(subject, organizationId, target.getId(), Roles.OWNER);

            verify(memberships, never()).findByOrganizationIdAndRoleName(any(), any());
        }
    }

    @Nested
    class Remove {

        @Test
        void anAgentLeavesTheOrganizationThemselves() {
            Membership mine = callerIs(Roles.AGENT);

            service.remove(subject, organizationId, mine.getId());

            verify(memberships).delete(mine);
        }

        @Test
        void anOwnerLeavesWhenAnotherOwnerRemains() {
            Membership mine = callerIs(Roles.OWNER);
            ownerCountIs(2);

            service.remove(subject, organizationId, mine.getId());

            verify(memberships).delete(mine);
        }

        @Test
        void theLastOwnerCannotLeave() {
            Membership mine = callerIs(Roles.OWNER);
            ownerCountIs(1);

            assertThrows(LastOwnerException.class, () -> service.remove(subject, organizationId, mine.getId()));
            verify(memberships, never()).delete(any(Membership.class));
        }

        @Test
        void anAdminRemovesAnAgent() {
            callerIs(Roles.ADMIN);
            Membership target = memberWith(Roles.AGENT);

            service.remove(subject, organizationId, target.getId());

            verify(memberships).delete(target);
        }

        @Test
        void anAgentCannotRemoveSomeoneElseAndTheMemberIsNotLoaded() {
            callerIs(Roles.AGENT);

            assertThrows(InsufficientOrganizationRoleException.class,
                    () -> service.remove(subject, organizationId, UUID.randomUUID()));
            verify(memberships, never()).findByIdAndOrganizationId(any(), any());
            verify(memberships, never()).delete(any(Membership.class));
        }

        @Test
        void anAdminCannotRemoveAnOwner() {
            callerIs(Roles.ADMIN);
            Membership target = memberWith(Roles.OWNER);

            assertThrows(InsufficientOrganizationRoleException.class,
                    () -> service.remove(subject, organizationId, target.getId()));
            verify(memberships, never()).delete(any(Membership.class));
        }

        @Test
        void removingAnUnknownOrForeignMemberIsNotFound() {
            callerIs(Roles.OWNER);
            UUID unknown = UUID.randomUUID();
            when(memberships.findByIdAndOrganizationId(unknown, organizationId)).thenReturn(Optional.empty());

            assertThrows(MemberNotFoundException.class, () -> service.remove(subject, organizationId, unknown));
        }

        @Test
        void aNonMemberGetsNotAMemberEvenForTheirOwnId() {
            when(memberships.findByOrganizationIdAndUserId(organizationId, caller.getId())).thenReturn(Optional.empty());

            assertThrows(NotAMemberException.class,
                    () -> service.remove(subject, organizationId, UUID.randomUUID()));
        }
    }
}
