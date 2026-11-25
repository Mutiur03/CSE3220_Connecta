package com.connecta.backend.inbox.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.connecta.backend.inbox.domain.AttachmentKind;
import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.inbox.domain.Conversation;
import com.connecta.backend.inbox.domain.Message;
import com.connecta.backend.inbox.domain.MessageAttachment;
import com.connecta.backend.inbox.dto.MessageResponse;
import com.connecta.backend.inbox.repository.MessageAttachmentRepository;
import com.connecta.backend.inbox.repository.MessageRepository;
import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.support.TestUsers;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MessageLookupServiceTests {

    @Mock
    MessageRepository messages;

    @Mock
    MessageAttachmentRepository attachments;

    @InjectMocks
    MessageLookupService service;

    private final UUID organizationId = UUID.randomUUID();
    private final Conversation thread = new Conversation(new Organization("Acme", "acme"), Channel.MESSENGER, "t");

    @Test
    void returnsTheMessageWithItsAttachmentsReadForTheSameOrganization() {
        Message message = Message.inbound(thread, null, "mid-1", "[Video]", Instant.parse("2026-11-24T10:00:00Z"));
        UUID messageId = UUID.randomUUID();
        TestUsers.set(message, "id", messageId);
        MessageAttachment video = new MessageAttachment(message, AttachmentKind.VIDEO, "https://cdn.example/v.mp4",
                "clip.mp4", null);
        when(messages.findByIdAndOrganizationId(messageId, organizationId)).thenReturn(Optional.of(message));
        when(attachments.findByMessage(organizationId, List.of(messageId))).thenReturn(Map.of(messageId,
                List.of(video)));

        MessageResponse found = service.find(organizationId, messageId).orElseThrow();

        assertEquals("[Video]", found.text());
        assertEquals(AttachmentKind.VIDEO, found.attachments().get(0).kind());
        assertEquals("clip.mp4", found.attachments().get(0).fileName());
    }

    @Test
    void anotherOrganizationsMessageIsEmptyAndNoAttachmentIsRead() {
        UUID messageId = UUID.randomUUID();
        when(messages.findByIdAndOrganizationId(messageId, organizationId)).thenReturn(Optional.empty());

        assertTrue(service.find(organizationId, messageId).isEmpty());
        verify(attachments, never()).findByMessage(any(), any());
    }
}
