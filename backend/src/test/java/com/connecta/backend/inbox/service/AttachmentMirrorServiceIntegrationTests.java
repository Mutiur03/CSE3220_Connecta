package com.connecta.backend.inbox.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.connecta.backend.config.AttachmentMirrorConfig;
import com.connecta.backend.file.service.StoredFileContent;
import com.connecta.backend.file.service.StoredFileService;
import com.connecta.backend.inbox.domain.AttachmentKind;
import com.connecta.backend.inbox.domain.MessageAttachment;
import com.connecta.backend.inbox.repository.MessageAttachmentRepository;
import com.connecta.backend.inbox.repository.MessageRepository;
import com.connecta.backend.meta.domain.MetaConnectedPage;
import com.connecta.backend.meta.domain.WebhookObject;
import com.connecta.backend.meta.repository.MetaConnectedPageRepository;
import com.connecta.backend.meta.service.CustomerProfileClientFactory;
import com.connecta.backend.meta.service.NormalizedAttachment;
import com.connecta.backend.meta.service.NormalizedMessage;
import com.connecta.backend.meta.service.NormalizedMessage.Direction;
import com.connecta.backend.meta.service.TokenCipher;
import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.org.repository.OrganizationRepository;
import com.connecta.backend.support.RandomJwtSecret;
import com.connecta.backend.support.TestClock;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The whole copy on real PostgreSQL with a fake downloader and a same-thread mirror executor: a received message is
 * committed, the after-commit listener copies its attachment into stored_files for the same organization, and the
 * attachment then serves our URL while keeping the Meta link. A replay stores nothing twice, a failed copy keeps
 * the Meta URL and waits for the retry window, and a history import is copied in its own batch. Skipped without
 * Docker. Nothing touches the network.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false",
        "app.public-base-url=https://api.example.com"})
@Import(TestClock.class)
@ContextConfiguration(initializers = RandomJwtSecret.class)
class AttachmentMirrorServiceIntegrationTests {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 7, 7};
    private static final String PUBLIC_FILES = "https://api.example.com/api/files/";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @MockitoBean
    MediaDownloader downloader;

    /** No customer profile lookup against Meta. */
    @MockitoBean
    CustomerProfileClientFactory profileClients;

    @TestBean(name = AttachmentMirrorConfig.EXECUTOR, methodName = "sameThread")
    TaskExecutor mirrorExecutor;

    static TaskExecutor sameThread() {
        return new SyncTaskExecutor();
    }

    @Autowired
    IncomingMessageService inbox;

    @Autowired
    AttachmentMirrorService mirror;

    @Autowired
    AttachmentMirrorStore store;

    @Autowired
    StoredFileService files;

    @Autowired
    OrganizationRepository organizations;

    @Autowired
    MetaConnectedPageRepository pages;

    @Autowired
    MessageRepository messages;

    @Autowired
    MessageAttachmentRepository attachments;

    @Autowired
    TokenCipher cipher;

    private Organization acme;
    private MetaConnectedPage page;

    @BeforeEach
    void setUp() {
        reset(downloader);
        acme = organizations.saveAndFlush(new Organization("Org " + UUID.randomUUID(), "org-" + UUID.randomUUID()));
        page = new MetaConnectedPage(acme, "p" + UUID.randomUUID());
        page.replace("Shop", cipher.encrypt("page-token"), null, TestClock.NOW);
        page = pages.saveAndFlush(page);
    }

    private static String metaUrl() {
        return "https://scontent.xx.fbcdn.net/v/" + UUID.randomUUID() + ".png?oh=token";
    }

    private static NormalizedMessage photo(String mid, String url) {
        return new NormalizedMessage(WebhookObject.PAGE, mid, "customer-" + mid, Direction.INBOUND, "[Photo]",
                TestClock.NOW, List.of(new NormalizedAttachment(AttachmentKind.IMAGE, url, "cat.png", null)));
    }

    private MessageAttachment attachmentOf(String mid) {
        UUID messageId = messages.findAll().stream().filter(m -> mid.equals(m.getExternalMessageId())).findFirst()
                .orElseThrow().getId();
        return attachments.findByMessage(acme.getId(), List.of(messageId)).get(messageId).get(0);
    }

    @Test
    void aReceivedAttachmentIsCopiedAfterCommitAndServedFromOurUrl() {
        String source = metaUrl();
        when(downloader.download(source)).thenReturn(new DownloadedMedia(PNG, "application/octet-stream"));
        String mid = "mid-" + UUID.randomUUID();

        inbox.receive(page, photo(mid, source));

        MessageAttachment copied = attachmentOf(mid);
        assertNotNull(copied.getStoredFileId());
        assertEquals(PUBLIC_FILES + copied.getStoredFileId(), copied.getUrl());
        assertEquals(source, copied.getSourceUrl());
        StoredFileContent stored = files.load(copied.getStoredFileId());
        assertArrayEquals(PNG, stored.bytes());
        assertEquals("image/png", stored.contentType());
        assertEquals("cat.png", stored.fileName());

        assertEquals(0, mirror.mirrorMessage(acme.getId(), copied.getMessage().getId()));
        assertEquals(0, mirror.mirrorPending());
        verify(downloader, times(1)).download(source);
    }

    @Test
    void aFailedCopyKeepsTheMetaUrlAndIsNotRetriedInsideTheRetryWindow() {
        String source = metaUrl();
        when(downloader.download(source)).thenThrow(new MediaDownloadException(MediaDownloadFailure.HTTP_ERROR));
        String mid = "mid-" + UUID.randomUUID();

        inbox.receive(page, photo(mid, source));

        MessageAttachment kept = attachmentOf(mid);
        assertNull(kept.getStoredFileId());
        assertEquals(source, kept.getUrl());
        assertEquals(TestClock.NOW, kept.getMirrorAttemptedAt());
        assertEquals(0, mirror.mirrorPending(acme.getId()));
        verify(downloader, times(1)).download(source);
    }

    @Test
    void aHistoryImportIsCopiedInItsOwnBatchOnlyForItsOrganization() {
        String source = metaUrl();
        when(downloader.download(source)).thenReturn(new DownloadedMedia(PNG, "image/png"));
        String mid = "hist-" + UUID.randomUUID();
        assertEquals(HistoryMessageOutcome.INSERTED, inbox.importHistorical(page, photo(mid, source)));
        verify(downloader, never()).download(anyString());

        assertEquals(0, mirror.mirrorPending(UUID.randomUUID()));
        assertEquals(1, mirror.mirrorPending(acme.getId()));

        MessageAttachment copied = attachmentOf(mid);
        assertTrue(copied.isMirrored());
        assertFalse(copied.getUrl().contains("fbcdn"));
    }

    @Test
    void aReconcileAddsOnlyTheMissingAttachmentAndOnlyThatOneIsCopied() {
        String first = metaUrl();
        String second = metaUrl();
        when(downloader.download(anyString())).thenReturn(new DownloadedMedia(PNG, "image/png"));
        String mid = "mid-" + UUID.randomUUID();
        inbox.receive(page, photo(mid, first));
        assertTrue(attachmentOf(mid).isMirrored());
        NormalizedMessage fromMeta = new NormalizedMessage(WebhookObject.PAGE, mid, "customer-" + mid,
                Direction.INBOUND, "[Photo]", TestClock.NOW, List.of(
                        new NormalizedAttachment(AttachmentKind.IMAGE, first, "cat.png", null),
                        new NormalizedAttachment(AttachmentKind.IMAGE, second, "dog.png", null)));

        assertEquals(HistoryMessageOutcome.UPDATED, inbox.importHistorical(page, fromMeta));
        assertEquals(1, mirror.mirrorPending(acme.getId()));
        assertEquals(HistoryMessageOutcome.UNCHANGED, inbox.importHistorical(page, fromMeta));

        verify(downloader, times(1)).download(first);
        verify(downloader, times(1)).download(second);
        UUID messageId = attachmentOf(mid).getMessage().getId();
        assertEquals(2, attachments.findByMessage(acme.getId(), List.of(messageId)).get(messageId).size());
    }

    @Test
    void aSharedPostIsStoredAsALinkAndNeverCopiedByTheListenerOrTheSweep() {
        String post = "https://www.instagram.com/p/" + UUID.randomUUID() + "/";
        String mid = "mid-" + UUID.randomUUID();
        NormalizedMessage shared = new NormalizedMessage(WebhookObject.PAGE, mid, "customer-" + mid,
                Direction.INBOUND, "[Shared post]", TestClock.NOW,
                List.of(NormalizedAttachment.link(post, "Shared post").orElseThrow()));

        inbox.receive(page, shared);

        MessageAttachment link = attachmentOf(mid);
        assertEquals(AttachmentKind.LINK, link.getKind());
        assertEquals(post, link.getUrl());
        assertNull(link.getMirrorAttemptedAt());
        assertEquals(0, mirror.mirrorPending(acme.getId()));
        assertEquals(0, mirror.mirrorPending());
        assertTrue(store.pendingOfMessage(acme.getId(), link.getMessage().getId()).isEmpty());
        verify(downloader, never()).download(anyString());
    }

    @Test
    void aReconcileAddsTheSharedPostLinkOnceAndCorrectsTheLabel() {
        String post = "https://www.instagram.com/p/" + UUID.randomUUID() + "/";
        String mid = "hist-" + UUID.randomUUID();
        inbox.receive(page, new NormalizedMessage(WebhookObject.PAGE, mid, "customer-" + mid, Direction.INBOUND,
                "[Attachment]", TestClock.NOW, List.of()));
        NormalizedMessage fromMeta = new NormalizedMessage(WebhookObject.PAGE, mid, "customer-" + mid,
                Direction.INBOUND, "[Shared post]", TestClock.NOW,
                List.of(NormalizedAttachment.link(post, "Shared post").orElseThrow()));

        assertEquals(HistoryMessageOutcome.UPDATED, inbox.importHistorical(page, fromMeta));
        assertEquals(HistoryMessageOutcome.UNCHANGED, inbox.importHistorical(page, fromMeta));

        UUID messageId = attachmentOf(mid).getMessage().getId();
        assertEquals("[Shared post]", messages.findById(messageId).orElseThrow().getBody());
        assertEquals(1, attachments.findByMessage(acme.getId(), List.of(messageId)).get(messageId).size());
        assertEquals(0, mirror.mirrorPending(acme.getId()));
        verify(downloader, never()).download(anyString());
    }

    @Test
    void aCopyCannotBeSavedThroughAnotherOrganization() {
        String source = metaUrl();
        when(downloader.download(source)).thenThrow(new MediaDownloadException(MediaDownloadFailure.NETWORK));
        String mid = "mid-" + UUID.randomUUID();
        inbox.receive(page, photo(mid, source));
        MessageAttachment attachment = attachmentOf(mid);

        MirrorCandidate foreign = new MirrorCandidate(attachment.getId(), UUID.randomUUID(), AttachmentKind.IMAGE,
                source, null);

        assertFalse(store.saveCopy(foreign, PNG, "image/png"));
        assertEquals(0, mirror.mirrorMessage(foreign.organizationId(), attachment.getMessage().getId()));
        assertNull(attachmentOf(mid).getStoredFileId());
    }
}
