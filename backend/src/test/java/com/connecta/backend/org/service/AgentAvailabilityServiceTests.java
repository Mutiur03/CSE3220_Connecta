package com.connecta.backend.org.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.connecta.backend.common.dto.PageResponse;
import com.connecta.backend.inbox.service.ConversationWorkloadService;
import com.connecta.backend.org.domain.AgentAvailability;
import com.connecta.backend.org.domain.AvailabilityStatus;
import com.connecta.backend.org.domain.Membership;
import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.org.dto.AssignableResponse;
import com.connecta.backend.org.dto.AvailabilityResponse;
import com.connecta.backend.org.repository.AgentAvailabilityRepository;
import com.connecta.backend.org.repository.MembershipRepository;
import com.connecta.backend.support.TestUsers;
import com.connecta.backend.user.domain.Role;
import com.connecta.backend.user.domain.Roles;
import com.connecta.backend.user.domain.User;
import com.connecta.backend.user.service.UserService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;

/** Built by hand so the real TenantAccessService and its role order run under the rules being tested. */
@ExtendWith(MockitoExtension.class)
class AgentAvailabilityServiceTests {

    @Mock
    AgentAvailabilityRepository availabilities;

    @Mock
    MembershipRepository memberships;

    @Mock
    PresenceService presence;

    @Mock
    UserService users;

    @Mock
    ConversationWorkloadService workload;

    AgentAvailabilityService service;

    private final UUID organizationId = UUID.randomUUID();
    private final Organization organization = organization();
    private final User caller = TestUsers.user("caller@example.com", Roles.AGENT);
    private final String subject = caller.getId().toString();

    @BeforeEach
    void setUp() {
        TenantAccessService tenants = new TenantAccessService(memberships, RoleHierarchyImpl.fromHierarchy(
                "ROLE_OWNER > ROLE_ADMIN\nROLE_ADMIN > ROLE_AGENT"));
        service = new AgentAvailabilityService(availabilities, memberships, presence, workload, tenants, new ActiveCaller(users));
        lenient().when(users.findEnabledById(caller.getId())).thenReturn(Optional.of(caller));
        lenient().when(workload.openCounts(eq(organizationId), anyCollection())).thenAnswer(call ->
                ((java.util.Collection<UUID>) call.getArgument(1)).stream()
                        .collect(java.util.stream.Collectors.toMap(id -> id, id -> 0L)));
        lenient().when(availabilities.saveAndFlush(any(AgentAvailability.class)))
                .thenAnswer(call -> call.getArgument(0));
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

    private Membership callerIs(String roleName) {
        Membership mine = membership(caller, roleName);
        when(memberships.findByOrganizationIdAndUserId(organizationId, caller.getId())).thenReturn(Optional.of(mine));
        return mine;
    }

    private void callerIsNotAMember() {
        when(memberships.findByOrganizationIdAndUserId(organizationId, caller.getId())).thenReturn(Optional.empty());
    }

    @Nested
    class SetOwn {

        @Test
        void theFirstChangeCreatesTheRow() {
            Membership mine = callerIs(Roles.AGENT);
            when(availabilities.findByMembershipIdAndOrganizationId(mine.getId(), organizationId))
                    .thenReturn(Optional.empty());

            AvailabilityResponse response = service.setOwn(subject, organizationId, AvailabilityStatus.AVAILABLE);

            assertEquals(AvailabilityStatus.AVAILABLE, response.status());
            assertEquals(mine.getId(), response.membershipId());
            assertEquals("caller@example.com", response.email());
            verify(availabilities).saveAndFlush(any(AgentAvailability.class));
        }

        @Test
        void aLaterChangeUpdatesTheExistingRow() {
            Membership mine = callerIs(Roles.AGENT);
            AgentAvailability row = new AgentAvailability(mine, AvailabilityStatus.AVAILABLE);
            when(availabilities.findByMembershipIdAndOrganizationId(mine.getId(), organizationId))
                    .thenReturn(Optional.of(row));

            AvailabilityResponse response = service.setOwn(subject, organizationId, AvailabilityStatus.AWAY);

            assertEquals(AvailabilityStatus.AWAY, response.status());
            assertEquals(AvailabilityStatus.AWAY, row.getStatus());
        }

        @Test
        void doesNotChangePresenceBecauseOnlyConnectionsDoThat() {
            Membership mine = callerIs(Roles.AGENT);
            when(availabilities.findByMembershipIdAndOrganizationId(mine.getId(), organizationId))
                    .thenReturn(Optional.empty());

            service.setOwn(subject, organizationId, AvailabilityStatus.AVAILABLE);
            service.setOwn(subject, organizationId, AvailabilityStatus.AWAY);

            verify(presence, never()).connected(any(), any());
            verify(presence, never()).disconnected(any(), any());
        }

        @Test
        void anAdminMaySetTheirOwnStatusToo() {
            Membership mine = callerIs(Roles.ADMIN);
            when(availabilities.findByMembershipIdAndOrganizationId(mine.getId(), organizationId))
                    .thenReturn(Optional.empty());

            assertEquals(Roles.ADMIN,
                    service.setOwn(subject, organizationId, AvailabilityStatus.AVAILABLE).role());
        }

        @Test
        void aNonMemberIsNotFoundAndNothingIsSaved() {
            callerIsNotAMember();

            assertThrows(NotAMemberException.class,
                    () -> service.setOwn(subject, organizationId, AvailabilityStatus.AVAILABLE));
            verify(availabilities, never()).saveAndFlush(any());
        }
    }

    @Nested
    class SetAssignable {

        @Test
        void theFirstSwitchCreatesTheRowAsAway() {
            callerIs(Roles.ADMIN);
            Membership target = membership(TestUsers.user("t@example.com", Roles.AGENT), Roles.AGENT);
            when(memberships.findByIdAndOrganizationId(target.getId(), organizationId)).thenReturn(Optional.of(target));
            when(availabilities.findByMembershipIdAndOrganizationId(target.getId(), organizationId))
                    .thenReturn(Optional.empty());

            AssignableResponse response = service.setAssignable(subject, organizationId, target.getId(), true);

            assertEquals(new AssignableResponse(target.getId(), true), response);
            org.mockito.ArgumentCaptor<AgentAvailability> saved = org.mockito.ArgumentCaptor
                    .forClass(AgentAvailability.class);
            verify(availabilities).saveAndFlush(saved.capture());
            assertEquals(AvailabilityStatus.AWAY, saved.getValue().getStatus());
        }

        @Test
        void aLaterSwitchTogglesTheExistingRowAndKeepsItsStatus() {
            callerIs(Roles.OWNER);
            Membership target = membership(TestUsers.user("t@example.com", Roles.AGENT), Roles.AGENT);
            AgentAvailability row = new AgentAvailability(target, AvailabilityStatus.AVAILABLE);
            row.changeAssignable(true);
            when(memberships.findByIdAndOrganizationId(target.getId(), organizationId)).thenReturn(Optional.of(target));
            when(availabilities.findByMembershipIdAndOrganizationId(target.getId(), organizationId))
                    .thenReturn(Optional.of(row));

            AssignableResponse response = service.setAssignable(subject, organizationId, target.getId(), false);

            assertEquals(false, response.assignable());
            assertEquals(AvailabilityStatus.AVAILABLE, row.getStatus());
        }

        @Test
        void anAgentIsDenied() {
            callerIs(Roles.AGENT);

            assertThrows(InsufficientOrganizationRoleException.class,
                    () -> service.setAssignable(subject, organizationId, UUID.randomUUID(), true));
            verify(availabilities, never()).saveAndFlush(any());
        }

        @Test
        void aNonMemberCallerIsNotFound() {
            callerIsNotAMember();

            assertThrows(NotAMemberException.class,
                    () -> service.setAssignable(subject, organizationId, UUID.randomUUID(), true));
        }

        @Test
        void aMembershipOfAnotherOrganizationIsNotFound() {
            callerIs(Roles.ADMIN);
            UUID foreign = UUID.randomUUID();
            when(memberships.findByIdAndOrganizationId(foreign, organizationId)).thenReturn(Optional.empty());

            assertThrows(MemberNotFoundException.class,
                    () -> service.setAssignable(subject, organizationId, foreign, true));
            verify(availabilities, never()).saveAndFlush(any());
        }
    }

    @Nested
    class GetOwn {

        @Test
        void withoutARowTheMemberIsAway() {
            Membership mine = callerIs(Roles.AGENT);
            when(availabilities.findByMembershipIdAndOrganizationId(mine.getId(), organizationId))
                    .thenReturn(Optional.empty());

            AvailabilityResponse response = service.getOwn(subject, organizationId);

            assertEquals(AvailabilityStatus.AWAY, response.status());
            assertNull(response.updatedAt());
            assertEquals(false, response.assignable());
        }

        @Test
        void withARowTheStoredStatusIsReturned() {
            Membership mine = callerIs(Roles.AGENT);
            when(availabilities.findByMembershipIdAndOrganizationId(mine.getId(), organizationId))
                    .thenReturn(Optional.of(new AgentAvailability(mine, AvailabilityStatus.AVAILABLE)));

            assertEquals(AvailabilityStatus.AVAILABLE, service.getOwn(subject, organizationId).status());
        }

        @Test
        void aMemberOfAnotherOrganizationIsNotFound() {
            callerIsNotAMember();

            assertThrows(NotAMemberException.class, () -> service.getOwn(subject, organizationId));
        }
    }

    @Nested
    class ListForOrganization {

        @Test
        void membersWithoutARowAreAwayAndOthersKeepTheirStatus() {
            callerIs(Roles.ADMIN);
            Membership withRow = membership(TestUsers.user("a@example.com", Roles.AGENT), Roles.AGENT);
            Membership withoutRow = membership(TestUsers.user("b@example.com", Roles.AGENT), Roles.AGENT);
            when(memberships.findByOrganizationId(eq(organizationId), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(withRow, withoutRow), PageRequest.of(0, 20), 2));
            when(availabilities.findByOrganizationIdAndMembershipIdIn(eq(organizationId), anyCollection()))
                    .thenReturn(List.of(new AgentAvailability(withRow, AvailabilityStatus.AVAILABLE)));

            PageResponse<AvailabilityResponse> page = service.list(subject, organizationId, 0, 20);

            assertEquals(2, page.totalElements());
            assertEquals(AvailabilityStatus.AVAILABLE, page.content().get(0).status());
            assertEquals(AvailabilityStatus.AWAY, page.content().get(1).status());
        }

        @Test
        void eachMemberCarriesTheirOpenConversationCount() {
            callerIs(Roles.ADMIN);
            Membership busy = membership(TestUsers.user("a@example.com", Roles.AGENT), Roles.AGENT);
            Membership idle = membership(TestUsers.user("b@example.com", Roles.AGENT), Roles.AGENT);
            when(memberships.findByOrganizationId(eq(organizationId), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(busy, idle), PageRequest.of(0, 20), 2));
            when(availabilities.findByOrganizationIdAndMembershipIdIn(eq(organizationId), anyCollection()))
                    .thenReturn(List.of());
            when(workload.openCounts(eq(organizationId), anyCollection()))
                    .thenReturn(Map.of(busy.getId(), 3L, idle.getId(), 0L));

            PageResponse<AvailabilityResponse> page = service.list(subject, organizationId, 0, 20);

            assertEquals(3L, page.content().get(0).openConversations());
            assertEquals(0L, page.content().get(1).openConversations());
        }

        @Test
        void anAgentIsForbidden() {
            callerIs(Roles.AGENT);

            assertThrows(InsufficientOrganizationRoleException.class,
                    () -> service.list(subject, organizationId, 0, 20));
            verify(memberships, never()).findByOrganizationId(any(), any());
        }

        @Test
        void aNonMemberIsNotFound() {
            callerIsNotAMember();

            assertThrows(NotAMemberException.class, () -> service.list(subject, organizationId, 0, 20));
        }
    }
}
