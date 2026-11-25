package com.connecta.backend.inbox.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.connecta.backend.auth.service.AccountNotAvailableException;
import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.inbox.domain.Conversation;
import com.connecta.backend.inbox.domain.ConversationParticipant;
import com.connecta.backend.inbox.domain.ConversationStatus;
import com.connecta.backend.inbox.domain.Participant;
import com.connecta.backend.inbox.dto.ConversationDetailResponse;
import com.connecta.backend.inbox.repository.ConversationParticipantRepository;
import com.connecta.backend.inbox.repository.ConversationRepository;
import com.connecta.backend.org.domain.Membership;
import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.org.repository.MembershipRepository;
import com.connecta.backend.org.service.ActiveCaller;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;

@ExtendWith(MockitoExtension.class)
class ConversationDetailServiceTests {

    @Mock
    ConversationRepository conversations;

    @Mock
    ConversationParticipantRepository participants;

    @Mock
    MembershipRepository memberships;

    @Mock
    UserService users;

    ConversationDetailService service;

    private final UUID organizationId = UUID.randomUUID();
    private final Organization organization = new Organization("Acme", "acme");
    private final User caller = TestUsers.user("caller@example.com", Roles.AGENT);
    private final String subject = caller.getId().toString();
    private final Conversation thread = new Conversation(organization, Channel.MESSENGER, "thread-1");

    @BeforeEach
    void setUp() {
        TestUsers.set(organization, "id", organizationId);
        TestUsers.set(thread, "id", UUID.randomUUID());
        TenantAccessService tenants = new TenantAccessService(memberships, RoleHierarchyImpl.fromHierarchy(
                "ROLE_OWNER > ROLE_ADMIN\nROLE_ADMIN > ROLE_AGENT"));
        service = new ConversationDetailService(conversations, participants, tenants, new ActiveCaller(users));
        lenient().when(users.findEnabledById(caller.getId())).thenReturn(Optional.of(caller));
    }

    private void callerHolds(String roleName) {
        Membership mine = new Membership(organization, caller, new Role(roleName));
        TestUsers.set(mine, "id", UUID.randomUUID());
        when(memberships.findByOrganizationIdAndUserId(organizationId, caller.getId())).thenReturn(Optional.of(mine));
    }

    private void threadExists() {
        when(conversations.findByIdAndOrganizationId(thread.getId(), organizationId)).thenReturn(Optional.of(thread));
    }

    @Nested
    class Get {

        @Test
        void anAgentGetsTheConversationWithItsCustomers() {
            callerHolds(Roles.AGENT);
            threadExists();
            Participant customer = new Participant(organization, Channel.MESSENGER, "psid-1", "Rina");
            TestUsers.set(customer, "id", UUID.randomUUID());
            when(participants.findByConversationIdAndOrganizationId(thread.getId(), organizationId))
                    .thenReturn(List.of(new ConversationParticipant(thread, customer)));

            ConversationDetailResponse detail = service.get(subject, organizationId, thread.getId());

            assertEquals(thread.getId(), detail.id());
            assertEquals(Channel.MESSENGER, detail.channel());
            assertEquals(ConversationStatus.OPEN, detail.status());
            assertEquals("Rina", detail.participants().get(0).displayName());
        }

        @Test
        void anAdminMayReadToo() {
            callerHolds(Roles.ADMIN);
            threadExists();

            assertEquals(thread.getId(), service.get(subject, organizationId, thread.getId()).id());
        }

        @Test
        void aNonMemberIsNotFound() {
            assertThrows(NotAMemberException.class, () -> service.get(subject, organizationId, thread.getId()));
            verify(conversations, never()).findByIdAndOrganizationId(thread.getId(), organizationId);
        }

        @Test
        void anotherTenantsConversationIsNotFound() {
            callerHolds(Roles.AGENT);

            assertThrows(ConversationNotFoundException.class,
                    () -> service.get(subject, organizationId, UUID.randomUUID()));
        }
    }

    @Nested
    class UpdateStatus {

        @Test
        void closesTheConversation() {
            callerHolds(Roles.AGENT);
            threadExists();
            when(conversations.saveAndFlush(thread)).thenReturn(thread);

            ConversationDetailResponse detail =
                    service.updateStatus(subject, organizationId, thread.getId(), ConversationStatus.CLOSED);

            assertEquals(ConversationStatus.CLOSED, detail.status());
            assertSame(ConversationStatus.CLOSED, thread.getStatus());
        }

        @Test
        void reopensAClosedConversation() {
            callerHolds(Roles.ADMIN);
            threadExists();
            thread.changeStatus(ConversationStatus.CLOSED);
            when(conversations.saveAndFlush(thread)).thenReturn(thread);

            assertEquals(ConversationStatus.OPEN,
                    service.updateStatus(subject, organizationId, thread.getId(), ConversationStatus.OPEN).status());
        }

        @Test
        void aNonMemberIsNotFoundAndNothingIsSaved() {
            assertThrows(NotAMemberException.class,
                    () -> service.updateStatus(subject, organizationId, thread.getId(), ConversationStatus.CLOSED));
            verify(conversations, never()).saveAndFlush(thread);
        }

        @Test
        void anotherTenantsConversationIsNotFound() {
            callerHolds(Roles.AGENT);

            assertThrows(ConversationNotFoundException.class, () -> service.updateStatus(subject, organizationId,
                    UUID.randomUUID(), ConversationStatus.CLOSED));
        }
    }

    @Nested
    class UpdateReadState {

        @Test
        void marksTheConversationRead() {
            callerHolds(Roles.AGENT);
            threadExists();
            thread.markUnread();

            service.updateReadState(subject, organizationId, thread.getId(), false);

            assertFalse(thread.isUnread());
            verify(conversations).saveAndFlush(thread);
        }

        @Test
        void anAdminMarksItUnreadAgain() {
            callerHolds(Roles.ADMIN);
            threadExists();

            service.updateReadState(subject, organizationId, thread.getId(), true);

            assertTrue(thread.isUnread());
        }

        @Test
        void aNonMemberIsNotFoundAndNothingIsSaved() {
            assertThrows(NotAMemberException.class,
                    () -> service.updateReadState(subject, organizationId, thread.getId(), false));
            verify(conversations, never()).saveAndFlush(thread);
        }

        @Test
        void anotherTenantsConversationIsNotFound() {
            callerHolds(Roles.AGENT);

            assertThrows(ConversationNotFoundException.class,
                    () -> service.updateReadState(subject, organizationId, UUID.randomUUID(), false));
        }
    }

    @Nested
    class Find {

        @Test
        void returnsTheConversationOfAMember() {
            callerHolds(Roles.AGENT);
            threadExists();

            assertSame(thread, service.find(subject, organizationId, thread.getId()));
        }

        @Test
        void aDisabledOrUnknownAccountIsRefused() {
            when(users.findEnabledById(caller.getId())).thenReturn(Optional.empty());

            assertThrows(AccountNotAvailableException.class,
                    () -> service.find(subject, organizationId, thread.getId()));
        }
    }
}
