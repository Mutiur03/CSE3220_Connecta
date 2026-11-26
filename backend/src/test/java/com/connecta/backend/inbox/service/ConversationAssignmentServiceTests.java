package com.connecta.backend.inbox.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.connecta.backend.common.dto.PageResponse;
import com.connecta.backend.inbox.domain.AssignmentSource;
import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.inbox.domain.Conversation;
import com.connecta.backend.inbox.domain.ConversationAssignment;
import com.connecta.backend.inbox.dto.ConversationAssigneeResponse;
import com.connecta.backend.inbox.dto.ConversationAssignmentResponse;
import com.connecta.backend.inbox.repository.ConversationAssignmentRepository;
import com.connecta.backend.inbox.repository.ConversationRepository;
import com.connecta.backend.org.domain.Membership;
import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.org.repository.MembershipRepository;
import com.connecta.backend.org.service.ActiveCaller;
import com.connecta.backend.org.service.InsufficientOrganizationRoleException;
import com.connecta.backend.org.service.MemberNotFoundException;
import com.connecta.backend.org.service.MemberService;
import com.connecta.backend.org.service.NotAMemberException;
import com.connecta.backend.org.service.TenantAccessService;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;

@ExtendWith(MockitoExtension.class)
class ConversationAssignmentServiceTests {

    @Mock
    ConversationRepository conversations;

    @Mock
    ConversationAssignmentRepository history;

    @Mock
    MemberService members;

    @Mock
    MembershipRepository memberships;

    @Mock
    UserService users;

    ConversationAssignmentService service;

    private final UUID organizationId = UUID.randomUUID();
    private final Organization organization = new Organization("Acme", "acme");
    private final User caller = TestUsers.user("boss@example.com", Roles.ADMIN);
    private final String subject = caller.getId().toString();
    private final Conversation thread = new Conversation(organization, Channel.MESSENGER, "thread-1");
    private final Membership rina = member("rina@example.com");
    private final Membership sam = member("sam@example.com");

    private Membership member(String email) {
        Membership m = new Membership(organization, TestUsers.user(email, Roles.AGENT), new Role(Roles.AGENT));
        TestUsers.set(m, "id", UUID.randomUUID());
        return m;
    }

    @BeforeEach
    void setUp() {
        TestUsers.set(organization, "id", organizationId);
        TestUsers.set(thread, "id", UUID.randomUUID());
        TenantAccessService tenants = new TenantAccessService(memberships, RoleHierarchyImpl.fromHierarchy(
                "ROLE_OWNER > ROLE_ADMIN\nROLE_ADMIN > ROLE_AGENT"));
        service = new ConversationAssignmentService(conversations, history, members, tenants,
                new ActiveCaller(users));
        lenient().when(users.findEnabledById(caller.getId())).thenReturn(Optional.of(caller));
        lenient().when(conversations.saveAndFlush(any(Conversation.class))).thenAnswer(i -> i.getArgument(0));
    }

    private void callerHolds(String roleName) {
        Membership mine = new Membership(organization, caller, new Role(roleName));
        when(memberships.findByOrganizationIdAndUserId(organizationId, caller.getId())).thenReturn(Optional.of(mine));
    }

    private void threadExists() {
        when(conversations.findByIdAndOrganizationId(thread.getId(), organizationId)).thenReturn(Optional.of(thread));
    }

    private void targetExists(Membership target) {
        when(members.findMember(organizationId, target.getId())).thenReturn(target);
    }

    private ConversationAssignment savedHistory() {
        ArgumentCaptor<ConversationAssignment> saved = ArgumentCaptor.forClass(ConversationAssignment.class);
        verify(history).save(saved.capture());
        return saved.getValue();
    }

    @Nested
    class Assign {

        @Test
        void assignsAnUnassignedConversationAndSavesManualHistory() {
            callerHolds(Roles.ADMIN);
            threadExists();
            targetExists(rina);

            ConversationAssigneeResponse result = service.assign(subject, organizationId, thread.getId(), rina.getId());

            assertSame(rina, thread.getAssignee());
            assertEquals(rina.getId(), result.membershipId());
            assertEquals(rina.getUser().getId(), result.userId());
            ConversationAssignment row = savedHistory();
            assertEquals(AssignmentSource.MANUAL, row.getSource());
            assertSame(caller, row.getAssignedBy());
            assertNull(row.getFromMember());
            assertSame(rina, row.getToMember());
        }

        @Test
        void reassignsToAnotherMemberAndRecordsTheOldOne() {
            callerHolds(Roles.ADMIN);
            threadExists();
            targetExists(sam);
            thread.assignTo(rina, caller, AssignmentSource.MANUAL);

            service.assign(subject, organizationId, thread.getId(), sam.getId());

            assertSame(sam, thread.getAssignee());
            ConversationAssignment row = savedHistory();
            assertSame(rina, row.getFromMember());
            assertSame(sam, row.getToMember());
        }

        @Test
        void aNullMembershipUnassigns() {
            callerHolds(Roles.ADMIN);
            threadExists();
            thread.assignTo(rina, caller, AssignmentSource.MANUAL);

            ConversationAssigneeResponse result = service.assign(subject, organizationId, thread.getId(), null);

            assertNull(thread.getAssignee());
            assertNull(result.membershipId());
            ConversationAssignment row = savedHistory();
            assertSame(rina, row.getFromMember());
            assertNull(row.getToMember());
        }

        @Test
        void aDisabledMemberIsRefused() {
            callerHolds(Roles.ADMIN);
            threadExists();
            rina.getUser().disable();
            targetExists(rina);

            assertThrows(AssigneeNotAvailableException.class,
                    () -> service.assign(subject, organizationId, thread.getId(), rina.getId()));
            verify(history, never()).save(any());
        }

        @Test
        void theCurrentAssigneeIsRefusedAndNothingIsSaved() {
            callerHolds(Roles.ADMIN);
            threadExists();
            targetExists(rina);
            thread.assignTo(rina, caller, AssignmentSource.MANUAL);

            assertThrows(AssignmentUnchangedException.class,
                    () -> service.assign(subject, organizationId, thread.getId(), rina.getId()));
            verify(history, never()).save(any());
        }

        @Test
        void unassigningAnUnassignedConversationIsRefused() {
            callerHolds(Roles.ADMIN);
            threadExists();

            assertThrows(AssignmentUnchangedException.class,
                    () -> service.assign(subject, organizationId, thread.getId(), null));
        }

        @Test
        void anotherTenantsConversationIsNotFound() {
            callerHolds(Roles.ADMIN);
            when(conversations.findByIdAndOrganizationId(thread.getId(), organizationId)).thenReturn(Optional.empty());

            assertThrows(ConversationNotFoundException.class,
                    () -> service.assign(subject, organizationId, thread.getId(), rina.getId()));
        }

        @Test
        void anotherTenantsMemberIsNotFound() {
            callerHolds(Roles.ADMIN);
            threadExists();
            when(members.findMember(organizationId, rina.getId())).thenThrow(new MemberNotFoundException());

            assertThrows(MemberNotFoundException.class,
                    () -> service.assign(subject, organizationId, thread.getId(), rina.getId()));
            verify(history, never()).save(any());
        }

        @Test
        void anAgentCannotAssign() {
            callerHolds(Roles.AGENT);

            assertThrows(InsufficientOrganizationRoleException.class,
                    () -> service.assign(subject, organizationId, thread.getId(), rina.getId()));
            verify(conversations, never()).findByIdAndOrganizationId(any(), any());
        }

        @Test
        void aNonMemberIsNotAMember() {
            when(memberships.findByOrganizationIdAndUserId(organizationId, caller.getId()))
                    .thenReturn(Optional.empty());

            assertThrows(NotAMemberException.class,
                    () -> service.assign(subject, organizationId, thread.getId(), rina.getId()));
        }
    }

    @Nested
    class ListHistory {

        @Test
        void returnsAPageNewestFirstScopedByOrganization() {
            callerHolds(Roles.ADMIN);
            threadExists();
            ConversationAssignment row = thread.assignTo(rina, caller, AssignmentSource.MANUAL);
            Page<ConversationAssignment> page = new PageImpl<>(List.of(row), PageRequest.of(0, 20), 1);
            when(history.findByConversationIdAndOrganizationId(any(), any(), any(Pageable.class))).thenReturn(page);

            PageResponse<ConversationAssignmentResponse> result =
                    service.list(subject, organizationId, thread.getId(), 0, 20);

            assertEquals(1, result.totalElements());
            assertEquals(AssignmentSource.MANUAL, result.content().get(0).source());
            ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
            verify(history).findByConversationIdAndOrganizationId(any(), any(), pageable.capture());
            assertEquals("createdAt: DESC,id: DESC", pageable.getValue().getSort().toString());
        }

        @Test
        void anAgentCannotReadTheHistory() {
            callerHolds(Roles.AGENT);

            assertThrows(InsufficientOrganizationRoleException.class,
                    () -> service.list(subject, organizationId, thread.getId(), 0, 20));
        }

        @Test
        void anotherTenantsConversationIsNotFound() {
            callerHolds(Roles.ADMIN);
            when(conversations.findByIdAndOrganizationId(thread.getId(), organizationId)).thenReturn(Optional.empty());

            assertThrows(ConversationNotFoundException.class,
                    () -> service.list(subject, organizationId, thread.getId(), 0, 20));
        }
    }
}
