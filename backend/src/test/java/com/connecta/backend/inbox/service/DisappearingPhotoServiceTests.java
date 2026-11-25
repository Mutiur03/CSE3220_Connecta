package com.connecta.backend.inbox.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.connecta.backend.file.service.StoredFileService;
import com.connecta.backend.inbox.domain.AttachmentKind;
import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.inbox.domain.Conversation;
import com.connecta.backend.inbox.domain.Message;
import com.connecta.backend.inbox.domain.MessageAttachment;
import com.connecta.backend.inbox.domain.MessageDirection;
import com.connecta.backend.inbox.domain.Participant;
import com.connecta.backend.inbox.repository.MessageAttachmentRepository;
import com.connecta.backend.inbox.repository.MessageRepository;
import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.support.TestClock;
import com.connecta.backend.support.TestUsers;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DisappearingPhotoServiceTests {

    private MessageRepository messages;
    private MessageAttachmentRepository attachments;
    private StoredFileService files;
    private DisappearingPhotoService service;
    private Organization acme;
    private Conversation thread;
    private Participant customer;

    @BeforeEach
    void setUp() {
        messages = mock(MessageRepository.class);
        attachments = mock(MessageAttachmentRepository.class);
        files = mock(StoredFileService.class);
        service = new DisappearingPhotoService(messages, attachments, files);
        acme = withId(new Organization("Acme", "acme"));
        thread = withId(new Conversation(acme, Channel.INSTAGRAM, "c"));
        customer = withId(new Participant(acme, Channel.INSTAGRAM, "c", null));
    }

    private static <T> T withId(T entity) {
        TestUsers.set(entity, "id", UUID.randomUUID());
        return entity;
    }

    private Message received(String body) {
        Message message = withId(Message.inbound(thread, customer, "m", body, TestClock.NOW));
        when(messages.findByOrganizationIdAndDirectionAndSentAt(acme.getId(), MessageDirection.INBOUND,
                TestClock.NOW)).thenReturn(List.of(message));
        return message;
    }

    @Test
    void aStoredPhotoBecomesTheNoteAndItsRowsAndFileAreDeleted() {
        Message message = received("[Photo]");
        MessageAttachment photo = withId(MessageAttachment.fromProvider(message, AttachmentKind.IMAGE,
                "https://cdn.example/a.jpg", null, null));
        UUID fileId = UUID.randomUUID();
        photo.mirrored(fileId, "https://app.example/api/files/" + fileId);
        when(attachments.findByMessage(acme.getId(), List.of(message.getId())))
                .thenReturn(Map.of(message.getId(), List.of(photo)));

        assertEquals(1, service.convert(acme.getId(), TestClock.NOW));

        assertEquals("[Disappearing photo]", message.getBody());
        verify(attachments).delete(photo);
        verify(files).delete(acme.getId(), fileId);
    }

    @Test
    void aReceivedMessageWithoutAnImageIsLeftAlone() {
        Message message = received("hello");
        when(attachments.findByMessage(acme.getId(), List.of(message.getId()))).thenReturn(Map.of());

        assertEquals(0, service.convert(acme.getId(), TestClock.NOW));

        assertEquals("hello", message.getBody());
        verify(attachments, never()).delete(any());
    }
}
