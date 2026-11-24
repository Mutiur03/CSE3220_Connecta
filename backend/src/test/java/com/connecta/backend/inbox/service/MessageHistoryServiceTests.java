package com.connecta.backend.inbox.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.connecta.backend.common.dto.PageResponse;
import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.inbox.domain.Conversation;
import com.connecta.backend.inbox.domain.Message;
import com.connecta.backend.inbox.domain.MessageDirection;
import com.connecta.backend.inbox.domain.Participant;
import com.connecta.backend.inbox.dto.MessageResponse;
import com.connecta.backend.inbox.repository.MessageRepository;
import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.org.service.NotAMemberException;
import com.connecta.backend.support.TestUsers;
import com.connecta.backend.user.domain.Roles;
import com.connecta.backend.user.domain.User;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class MessageHistoryServiceTests {

    @Mock
    ConversationDetailService conversations;

    @Mock
    MessageRepository messages;

    @InjectMocks
    MessageHistoryService service;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();
    private final Organization organization = new Organization("Acme", "acme");
    private final Conversation thread = new Conversation(organization, Channel.INSTAGRAM, "thread-1");
    private final Instant sentAt = Instant.parse("2026-11-24T10:00:00Z");

    @Test
    void returnsOnePageNewestFirstWithAStableTiebreak() {
        when(messages.findByConversationIdAndOrganizationId(any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.list("sub", organizationId, conversationId, 2, 50);

        ArgumentCaptor<Pageable> paging = ArgumentCaptor.forClass(Pageable.class);
        verify(messages).findByConversationIdAndOrganizationId(any(), any(), paging.capture());
        assertEquals(2, paging.getValue().getPageNumber());
        assertEquals(50, paging.getValue().getPageSize());
        assertEquals(Sort.Direction.DESC, paging.getValue().getSort().getOrderFor("sentAt").getDirection());
        assertEquals(Sort.Direction.DESC, paging.getValue().getSort().getOrderFor("id").getDirection());
    }

    @Test
    void mapsAnInboundMessageToItsCustomer() {
        Participant customer = new Participant(organization, Channel.INSTAGRAM, "igsid", "Rina");
        TestUsers.set(customer, "id", UUID.randomUUID());
        Message inbound = Message.inbound(thread, customer, "mid-1", "hello", sentAt);
        when(messages.findByConversationIdAndOrganizationId(any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(inbound)));

        PageResponse<MessageResponse> page = service.list("sub", organizationId, conversationId, 0, 20);

        MessageResponse only = page.content().get(0);
        assertEquals(MessageDirection.INBOUND, only.direction());
        assertEquals("hello", only.text());
        assertEquals(sentAt, only.sentAt());
        assertEquals(customer.getId(), only.senderId());
        assertEquals("Rina", only.senderName());
        assertEquals(1, page.totalElements());
    }

    @Test
    void mapsAnOutboundMessageToItsAgentAndSurvivesADeletedAgent() {
        User agent = TestUsers.user("agent@example.com", Roles.AGENT);
        Message sent = Message.outbound(thread, agent, "mid-2", "hi", sentAt);
        Message orphan = Message.outbound(thread, null, "mid-3", "bye", sentAt);
        when(messages.findByConversationIdAndOrganizationId(any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sent, orphan)));

        List<MessageResponse> content = service.list("sub", organizationId, conversationId, 0, 20).content();

        assertEquals(agent.getId(), content.get(0).senderId());
        assertEquals("Test User", content.get(0).senderName());
        assertNull(content.get(1).senderId());
    }

    @Test
    void anUnknownOrForeignConversationIsNotFoundAndNoMessagesAreRead() {
        when(conversations.find("sub", organizationId, conversationId)).thenThrow(new ConversationNotFoundException());

        assertThrows(ConversationNotFoundException.class,
                () -> service.list("sub", organizationId, conversationId, 0, 20));
        verify(messages, never()).findByConversationIdAndOrganizationId(any(), any(), any(Pageable.class));
    }

    @Test
    void aNonMemberIsNotFound() {
        when(conversations.find("sub", organizationId, conversationId)).thenThrow(new NotAMemberException());

        assertThrows(NotAMemberException.class, () -> service.list("sub", organizationId, conversationId, 0, 20));
    }
}
