package com.connecta.backend.inbox.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.connecta.backend.inbox.domain.AttachmentKind;
import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.inbox.domain.Conversation;
import com.connecta.backend.inbox.domain.Message;
import com.connecta.backend.inbox.domain.MessageAttachment;
import com.connecta.backend.inbox.domain.Participant;
import com.connecta.backend.inbox.repository.MessageAttachmentRepository;
import com.connecta.backend.meta.domain.WebhookObject;
import com.connecta.backend.meta.service.NormalizedAttachment;
import com.connecta.backend.meta.service.NormalizedMessage;
import com.connecta.backend.meta.service.NormalizedMessage.Direction;
import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.support.TestClock;
import com.connecta.backend.support.TestUsers;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MessageReconcilerTests {

    private static final String META_PHOTO = "https://cdn.example/p.jpg";
    private static final String META_FILE = "https://cdn.example/f.pdf";
    private static final String OUR_COPY = "https://connecta.example/api/files/x";

    private MessageAttachmentRepository attachments;
    private MessageReconciler reconciler;
    private Organization acme;
    private Participant customer;
    private Conversation thread;

    @BeforeEach
    void setUp() {
        attachments = mock(MessageAttachmentRepository.class);
        reconciler = new MessageReconciler(attachments);
        acme = withId(new Organization("Acme", "acme"));
        thread = withId(new Conversation(acme, Channel.MESSENGER, "customer-1"));
        customer = withId(new Participant(acme, Channel.MESSENGER, "customer-1", null));
    }

    private static <T> T withId(T entity) {
        TestUsers.set(entity, "id", UUID.randomUUID());
        return entity;
    }

    private Message stored(String body) {
        return withId(Message.inbound(thread, customer, "m.1", body, TestClock.NOW));
    }

    private void holding(Message message, MessageAttachment... held) {
        when(attachments.findByMessage(acme.getId(), List.of(message.getId())))
                .thenReturn(held.length == 0 ? Map.of() : Map.of(message.getId(), List.of(held)));
    }

    private static NormalizedMessage fromMeta(String text, String... urls) {
        List<NormalizedAttachment> files = Arrays.stream(urls)
                .map(url -> new NormalizedAttachment(AttachmentKind.IMAGE, url, null, "image/jpeg")).toList();
        return new NormalizedMessage(WebhookObject.PAGE, "m.1", "customer-1", Direction.INBOUND, text,
                TestClock.NOW.plusSeconds(99), files);
    }

    private static MessageAttachment metaMedia(Message message, String url) {
        return withId(MessageAttachment.fromProvider(message, AttachmentKind.IMAGE, url, null, null));
    }

    @SuppressWarnings("unchecked")
    private List<MessageAttachment> added() {
        ArgumentCaptor<List<MessageAttachment>> saved = ArgumentCaptor.forClass(List.class);
        verify(attachments).saveAll(saved.capture());
        return saved.getValue();
    }

    @Nested
    class Body {

        @Test
        void aLabelIsCorrectedWhenMetaNowKnowsTheMedia() {
            Message message = stored("[Attachment]");
            holding(message, metaMedia(message, META_PHOTO));

            assertTrue(reconciler.reconcile(message, fromMeta("[Photo]", META_PHOTO)));

            assertEquals("[Photo]", message.getBody());
        }

        @Test
        void theGenericLabelWithoutMediaNeverOverwritesAStoredBody() {
            Message message = stored("[Photo]");

            assertFalse(reconciler.reconcile(message, fromMeta("[Attachment]")));

            assertEquals("[Photo]", message.getBody());
        }

        @Test
        void onlyTheBodyChangesNeverTheSenderConversationOrTime() {
            Message message = stored("Helo");

            assertTrue(reconciler.reconcile(message, fromMeta("Hello")));

            assertEquals("Hello", message.getBody());
            assertSame(customer, message.getSenderParticipant());
            assertSame(thread, message.getConversation());
            assertEquals(TestClock.NOW, message.getSentAt());
            assertFalse(thread.isUnread());
        }
    }

    @Nested
    class Attachments {

        @Test
        void attachmentsTheMessageLacksAreAddedAsMetaMediaForTheMirror() {
            Message message = stored("[Photo]");
            holding(message);

            assertTrue(reconciler.reconcile(message, fromMeta("[Photo]", META_PHOTO, META_FILE)));

            List<MessageAttachment> added = added();
            assertEquals(2, added.size());
            assertSame(message, added.get(0).getMessage());
            assertEquals(acme, added.get(0).getOrganization());
            assertEquals(META_PHOTO, added.get(0).getSourceUrl());
            assertNull(added.get(0).getStoredFileId());
            assertEquals(META_FILE, added.get(1).getUrl());
        }

        @Test
        void onlyTheMissingOneIsAddedSoOnlyItGoesToTheMirror() {
            Message message = stored("[Photo]");
            holding(message, metaMedia(message, META_PHOTO));

            reconciler.reconcile(message, fromMeta("[Photo]", META_PHOTO, META_FILE));

            List<MessageAttachment> added = added();
            assertEquals(1, added.size());
            assertEquals(META_FILE, added.get(0).getSourceUrl());
        }

        @Test
        void aCopiedAttachmentIsMatchedByItsSourceUrlAndNotAddedAgain() {
            Message message = stored("[Photo]");
            MessageAttachment copied = metaMedia(message, META_PHOTO);
            copied.mirrored(UUID.randomUUID(), OUR_COPY);
            holding(message, copied);

            assertFalse(reconciler.reconcile(message, fromMeta("[Photo]", META_PHOTO)));

            verify(attachments, never()).saveAll(anyList());
        }

        @Test
        void anAttachmentOurAgentSentUnderOurOwnUrlIsNotDuplicated() {
            Message message = stored("[Photo]");
            holding(message, withId(new MessageAttachment(message, AttachmentKind.IMAGE, OUR_COPY, "p.jpg",
                    "image/jpeg")));

            assertFalse(reconciler.reconcile(message, fromMeta("[Photo]", META_PHOTO)));

            verify(attachments, never()).saveAll(anyList());
        }

        @Test
        void anAttachmentAbsentFromMetaIsKept() {
            Message message = stored("[Photo]");
            holding(message, metaMedia(message, META_FILE));

            assertFalse(reconciler.reconcile(message, fromMeta("[Photo]", META_PHOTO)));

            verify(attachments, never()).delete(any());
            verify(attachments, never()).deleteAll(anyList());
            verify(attachments, never()).saveAll(anyList());
        }
    }

    @Nested
    class SharedPosts {

        private static final String POST = "https://www.instagram.com/p/abc/";

        private NormalizedMessage sharedPost() {
            return new NormalizedMessage(WebhookObject.INSTAGRAM, "m.1", "customer-1", Direction.INBOUND,
                    "[Shared post]", TestClock.NOW, List.of(NormalizedAttachment.link(POST, "Shared post")
                            .orElseThrow()));
        }

        @Test
        void aLabelOnlyMessageGetsTheLinkOnceAndItsLabelCorrected() {
            Message message = stored("[Attachment]");
            holding(message);

            assertTrue(reconciler.reconcile(message, sharedPost()));

            assertEquals("[Shared post]", message.getBody());
            List<MessageAttachment> added = added();
            assertEquals(1, added.size());
            assertEquals(AttachmentKind.LINK, added.get(0).getKind());
            assertEquals(POST, added.get(0).getUrl());
            assertEquals("Shared post", added.get(0).getFileName());
            assertNull(added.get(0).getContentType());
        }

        @Test
        void aRerunFindsTheLinkByUrlAndAddsNothing() {
            Message message = stored("[Shared post]");
            holding(message, withId(MessageAttachment.fromProvider(message, AttachmentKind.LINK, POST,
                    "Shared post", null)));

            assertFalse(reconciler.reconcile(message, sharedPost()));

            verify(attachments, never()).saveAll(anyList());
        }
    }

    @Nested
    class Unchanged {

        @Test
        void aMessageThatMatchesMetaWritesNothing() {
            Message message = stored("[Photo]");
            holding(message, metaMedia(message, META_PHOTO));

            assertFalse(reconciler.reconcile(message, fromMeta("[Photo]", META_PHOTO)));

            assertEquals("[Photo]", message.getBody());
            verify(attachments, never()).saveAll(anyList());
        }

        @Test
        void theAttachmentLookupIsScopedToTheMessagesOrganization() {
            Message message = stored("[Photo]");
            holding(message);

            reconciler.reconcile(message, fromMeta("[Photo]", META_PHOTO));

            verify(attachments).findByMessage(acme.getId(), List.of(message.getId()));
        }
    }
}
