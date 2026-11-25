package com.connecta.backend.inbox.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.connecta.backend.config.AttachmentMirrorProperties;
import com.connecta.backend.file.service.StoredFileService;
import com.connecta.backend.inbox.domain.AttachmentKind;
import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.inbox.domain.Conversation;
import com.connecta.backend.inbox.domain.Message;
import com.connecta.backend.inbox.domain.MessageAttachment;
import com.connecta.backend.inbox.repository.MessageAttachmentRepository;
import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.support.TestClock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;

class AttachmentMirrorStoreTests {

    private static final String SOURCE = "https://scontent.fbcdn.net/a.png";
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G'};

    private MessageAttachmentRepository attachments;
    private StoredFileService files;
    private ApplicationEventPublisher publisher;
    private AttachmentMirrorStore store;
    private Organization acme;
    private Conversation conversation;
    private MessageAttachment attachment;
    private MirrorCandidate candidate;

    @BeforeEach
    void setUp() {
        attachments = mock(MessageAttachmentRepository.class);
        files = mock(StoredFileService.class);
        publisher = mock(ApplicationEventPublisher.class);
        AttachmentMirrorProperties settings = new AttachmentMirrorProperties(null, null, Duration.ofDays(3),
                Duration.ofMinutes(15), 20, null, null, null);
        store = new AttachmentMirrorStore(attachments, files, settings, publisher, TestClock.FIXED);
        acme = withId(new Organization("Acme", "acme"));
        conversation = withId(new Conversation(acme, Channel.MESSENGER, "thread"));
        Message message = withId(Message.inbound(conversation, null, "m1", "[Photo]", TestClock.NOW));
        attachment = withId(MessageAttachment.fromProvider(message, AttachmentKind.IMAGE, SOURCE, "a.png", null));
        candidate = MirrorCandidate.from(attachment);
    }

    private static <T> T withId(T entity) {
        ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
        return entity;
    }

    @Nested
    class Pending {

        @Test
        void theSweepAsksForYoungUncopiedAttachmentsNotTriedRecentlyInOneBoundedBatch() {
            when(attachments.findMirrorPending(TestClock.NOW.minus(Duration.ofDays(3)),
                    TestClock.NOW.minus(Duration.ofMinutes(15)), Limit.of(20))).thenReturn(List.of(attachment));

            assertEquals(List.of(candidate), store.pending());
        }

        @Test
        void theImportBatchIsScopedToItsOrganization() {
            when(attachments.findMirrorPending(acme.getId(), TestClock.NOW.minus(Duration.ofDays(3)),
                    TestClock.NOW.minus(Duration.ofMinutes(15)), Limit.of(20))).thenReturn(List.of(attachment));

            assertEquals(List.of(candidate), store.pending(acme.getId()));
        }

        @Test
        void aCandidateCarriesItsOwnOrganizationAndTheMetaSource() {
            assertEquals(acme.getId(), candidate.organizationId());
            assertEquals(SOURCE, candidate.sourceUrl());
        }
    }

    @Nested
    class Claim {

        @Test
        void onlyTheWorkerWhoseUpdateHitTheRowMayDownload() {
            when(attachments.claimMirror(candidate.attachmentId(), acme.getId(), TestClock.NOW,
                    TestClock.NOW.minus(Duration.ofMinutes(15)))).thenReturn(1, 0);

            assertTrue(store.claim(candidate));
            assertFalse(store.claim(candidate));
        }
    }

    @Nested
    class SaveCopy {

        @Test
        void storesTheFileForTheAttachmentsOrganizationAndServesTheCopy() {
            UUID fileId = UUID.randomUUID();
            when(attachments.findForMirrorUpdate(attachment.getId(), acme.getId()))
                    .thenReturn(Optional.of(attachment));
            when(files.store(acme.getId(), PNG, "image/png", "a.png")).thenReturn(fileId);
            when(files.publicUrl(fileId)).thenReturn("https://api.example.com/api/files/" + fileId);

            assertTrue(store.saveCopy(candidate, PNG, "image/png"));

            assertEquals(fileId, attachment.getStoredFileId());
            assertEquals("https://api.example.com/api/files/" + fileId, attachment.getUrl());
            assertEquals(SOURCE, attachment.getSourceUrl());
            verify(publisher).publishEvent(new AttachmentMirroredEvent(acme.getId(), conversation.getId()));
        }

        @Test
        void anAttachmentCopiedMeanwhileGetsNoSecondFile() {
            attachment.mirrored(UUID.randomUUID(), "https://api.example.com/api/files/x");
            when(attachments.findForMirrorUpdate(attachment.getId(), acme.getId()))
                    .thenReturn(Optional.of(attachment));

            assertFalse(store.saveCopy(candidate, PNG, "image/png"));

            verifyNoInteractions(files, publisher);
        }

        @Test
        void anotherOrganizationsIdFindsNothingAndStoresNothing() {
            MirrorCandidate foreign = new MirrorCandidate(attachment.getId(), UUID.randomUUID(),
                    AttachmentKind.IMAGE, SOURCE, null);
            when(attachments.findForMirrorUpdate(any(), any())).thenReturn(Optional.empty());

            assertFalse(store.saveCopy(foreign, PNG, "image/png"));

            verify(attachments).findForMirrorUpdate(attachment.getId(), foreign.organizationId());
            verifyNoInteractions(files, publisher);
        }
    }
}
