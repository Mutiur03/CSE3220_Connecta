package com.connecta.backend.inbox.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.connecta.backend.common.dto.PageResponse;
import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.inbox.domain.Conversation;
import com.connecta.backend.inbox.domain.ConversationParticipant;
import com.connecta.backend.inbox.domain.ConversationStatus;
import com.connecta.backend.inbox.domain.Message;
import com.connecta.backend.inbox.domain.Participant;
import com.connecta.backend.inbox.dto.ConversationSummaryResponse;
import com.connecta.backend.inbox.repository.ConversationParticipantRepository;
import com.connecta.backend.inbox.repository.ConversationRepository;
import com.connecta.backend.org.domain.Membership;
import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.org.repository.MembershipRepository;
import com.connecta.backend.org.service.ActiveCaller;
import com.connecta.backend.org.service.NotAMemberException;
import com.connecta.backend.org.service.TenantAccessService;
import com.connecta.backend.support.TestClock;
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
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;

/** Built by hand so the real TenantAccessService runs under the rule being tested. */
@ExtendWith(MockitoExtension.class)
class ConversationQueryServiceTests {

    @Mock
    ConversationRepository conversations;

    @Mock
    ConversationParticipantRepository links;

    @Mock
    MembershipRepository memberships;

    @Mock
    UserService users;

    ConversationQueryService service;

    private final UUID organizationId = UUID.randomUUID();
    private final Organization organization = organization();
    private final User caller = TestUsers.user("caller@example.com", Roles.AGENT);
    private final String subject = caller.getId().toString();
    private final ConversationFilter noFilter = new ConversationFilter(null, null, null);

    @BeforeEach
    void setUp() {
        TenantAccessService tenants = new TenantAccessService(memberships, RoleHierarchyImpl.fromHierarchy(
                "ROLE_OWNER > ROLE_ADMIN\nROLE_ADMIN > ROLE_AGENT"));
        service = new ConversationQueryService(conversations, links, tenants, new ActiveCaller(users));
        lenient().when(users.findEnabledById(caller.getId())).thenReturn(Optional.of(caller));
    }

    private Organization organization() {
        Organization org = new Organization("Acme", "acme");
        TestUsers.set(org, "id", organizationId);
        return org;
    }

    private void callerIs(String roleName) {
        when(memberships.findByOrganizationIdAndUserId(organizationId, caller.getId()))
                .thenReturn(Optional.of(new Membership(organization, caller, new Role(roleName))));
    }

    private Conversation conversation(Channel channel) {
        Conversation conversation = new Conversation(organization, channel, "thread-" + UUID.randomUUID());
        TestUsers.set(conversation, "id", UUID.randomUUID());
        conversation.recordMessageAt(TestClock.NOW);
        return conversation;
    }

    private void pageOf(Conversation... found) {
        when(conversations.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(call -> new PageImpl<>(List.of(found), call.getArgument(1, Pageable.class),
                        found.length));
    }

    @Nested
    class Access {

        @Test
        void anAgentMayList() {
            callerIs(Roles.AGENT);
            pageOf();

            assertEquals(0, service.list(subject, organizationId, noFilter, 0, 20).totalElements());
        }

        @Test
        void anAdminMayList() {
            callerIs(Roles.ADMIN);
            pageOf();

            assertEquals(0, service.list(subject, organizationId, noFilter, 0, 20).totalElements());
        }

        @Test
        void aNonMemberGetsNotAMemberAndNothingIsRead() {
            when(memberships.findByOrganizationIdAndUserId(organizationId, caller.getId()))
                    .thenReturn(Optional.empty());

            assertThrows(NotAMemberException.class, () -> service.list(subject, organizationId, noFilter, 0, 20));
            verify(conversations, never()).findAll(any(Specification.class), any(Pageable.class));
        }
    }

    @Nested
    class Summaries {

        @Test
        void eachRowCarriesChannelCustomerNameAndLastMessagePreview() {
            callerIs(Roles.AGENT);
            Conversation instagram = conversation(Channel.INSTAGRAM);
            Conversation messenger = conversation(Channel.MESSENGER);
            pageOf(instagram, messenger);
            Participant ann = new Participant(organization, Channel.INSTAGRAM, "ig-1", "Ann");
            when(links.findWithParticipantByConversationIdIn(anyCollection()))
                    .thenReturn(List.of(new ConversationParticipant(instagram, ann)));
            when(conversations.findLastMessages(anyCollection())).thenReturn(List.of(
                    Message.inbound(instagram, ann, "m1", "  Hello there  ", TestClock.NOW)));

            PageResponse<ConversationSummaryResponse> page = service.list(subject, organizationId, noFilter, 0, 20);

            assertEquals(2, page.content().size());
            ConversationSummaryResponse first = page.content().get(0);
            assertEquals(instagram.getId(), first.id());
            assertEquals(Channel.INSTAGRAM, first.channel());
            assertEquals(ConversationStatus.OPEN, first.status());
            assertEquals("Ann", first.participantName());
            assertEquals("Hello there", first.lastMessagePreview());
            assertEquals(TestClock.NOW, first.lastMessageAt());
            ConversationSummaryResponse second = page.content().get(1);
            assertEquals(Channel.MESSENGER, second.channel());
            assertNull(second.participantName());
            assertNull(second.lastMessagePreview());
        }

        @Test
        void aLongMessageIsCutToThePreviewLength() {
            callerIs(Roles.AGENT);
            Conversation conversation = conversation(Channel.MESSENGER);
            pageOf(conversation);
            when(links.findWithParticipantByConversationIdIn(anyCollection())).thenReturn(List.of());
            when(conversations.findLastMessages(anyCollection())).thenReturn(List.of(Message.inbound(conversation,
                    null, "m1", "x".repeat(ConversationSummaryResponse.PREVIEW_MAX_LENGTH + 50), TestClock.NOW)));

            ConversationSummaryResponse row = service.list(subject, organizationId, noFilter, 0, 20).content().get(0);

            assertEquals(ConversationSummaryResponse.PREVIEW_MAX_LENGTH, row.lastMessagePreview().length());
        }

        @Test
        void anEmptyPageSkipsTheBatchQueries() {
            callerIs(Roles.AGENT);
            pageOf();

            PageResponse<ConversationSummaryResponse> page = service.list(subject, organizationId, noFilter, 0, 20);

            assertEquals(List.of(), page.content());
            verify(links, never()).findWithParticipantByConversationIdIn(anyCollection());
            verify(conversations, never()).findLastMessages(anyCollection());
        }
    }

    @Nested
    class Paging {

        @Test
        void sizeIsClampedToTheMaximumAndSortedNewestFirst() {
            callerIs(Roles.AGENT);
            pageOf();

            service.list(subject, organizationId, noFilter, 2, 500);

            ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
            verify(conversations).findAll(any(Specification.class), pageable.capture());
            assertEquals(PageResponse.MAX_SIZE, pageable.getValue().getPageSize());
            assertEquals(2, pageable.getValue().getPageNumber());
            assertEquals(2, pageable.getValue().getSort().stream().count());
        }

        @Test
        void aSmallSizeIsKept() {
            callerIs(Roles.AGENT);
            pageOf();

            service.list(subject, organizationId, noFilter, 0, 5);

            ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
            verify(conversations).findAll(any(Specification.class), pageable.capture());
            assertEquals(5, pageable.getValue().getPageSize());
        }
    }

    @Nested
    class Filters {

        @Test
        void aClosedConversationReportsItsStatus() {
            callerIs(Roles.AGENT);
            Conversation closed = conversation(Channel.MESSENGER);
            closed.changeStatus(ConversationStatus.CLOSED);
            pageOf(closed);
            when(links.findWithParticipantByConversationIdIn(anyCollection())).thenReturn(List.of());
            when(conversations.findLastMessages(anyCollection())).thenReturn(List.of());

            ConversationSummaryResponse row = service.list(subject, organizationId, noFilter, 0, 20).content().get(0);

            assertEquals(ConversationStatus.CLOSED, row.status());
        }

        @Test
        void channelNameAndStatusFiltersAreAccepted() {
            callerIs(Roles.AGENT);
            pageOf();

            service.list(subject, organizationId, new ConversationFilter(Channel.INSTAGRAM, " ann ",
                    ConversationStatus.OPEN), 0, 20);

            verify(conversations).findAll(any(Specification.class), any(Pageable.class));
        }

        @Test
        void aBlankNameIsIgnored() {
            callerIs(Roles.AGENT);
            pageOf();

            service.list(subject, organizationId, new ConversationFilter(null, "   ", null), 0, 20);

            verify(conversations).findAll(any(Specification.class), any(Pageable.class));
        }
    }
}
