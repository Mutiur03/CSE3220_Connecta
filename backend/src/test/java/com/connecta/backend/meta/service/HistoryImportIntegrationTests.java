package com.connecta.backend.meta.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.connecta.backend.inbox.domain.AttachmentKind;
import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.inbox.domain.Participant;
import com.connecta.backend.inbox.repository.ParticipantRepository;
import com.connecta.backend.inbox.service.HistoryImportedEvent;
import com.connecta.backend.inbox.service.IncomingMessageService;
import com.connecta.backend.inbox.service.MessageReceivedEvent;
import com.connecta.backend.meta.domain.HistoryImportFailure;
import com.connecta.backend.meta.domain.MetaConnectedPage;
import com.connecta.backend.meta.domain.WebhookObject;
import com.connecta.backend.meta.repository.MetaConnectedPageRepository;
import com.connecta.backend.meta.service.NormalizedMessage.Direction;
import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.org.repository.OrganizationRepository;
import com.connecta.backend.support.RandomJwtSecret;
import com.connecta.backend.support.TestClock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The history import and its startup catch-up on real PostgreSQL (Flyway runs, including the history_imported_at
 * column). The Meta boundary is stubbed, so nothing leaves the test. Each test uses its own organization.
 * Skipped without Docker.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false",
        "app.meta.history-import.enabled=true"})
@Import(TestClock.class)
@ContextConfiguration(initializers = RandomJwtSecret.class)
@RecordApplicationEvents
class HistoryImportIntegrationTests {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @MockitoBean
    ConversationHistoryClientFactory clients;

    @Autowired
    HistoryImportService imports;

    @Autowired
    HistoryImportTrigger trigger;

    @Autowired
    HistoryAccountStore store;

    @Autowired
    OrganizationRepository organizations;

    @Autowired
    MetaConnectedPageRepository pages;

    @Autowired
    ParticipantRepository participants;

    @Autowired
    TokenCipher cipher;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    IncomingMessageService inbox;

    @Autowired
    ApplicationEvents published;

    private static NormalizedMessage message(String id, String peer, Direction direction, String sentAt) {
        return new NormalizedMessage(WebhookObject.PAGE, id, peer, direction, "text " + id, Instant.parse(sentAt),
                List.of());
    }

    private HistoryConversation rina(String suffix) {
        return new HistoryConversation("rina-" + suffix, "Rina Akter", List.of(
                message("a1-" + suffix, "rina-" + suffix, Direction.INBOUND, "2026-11-20T10:00:00Z"),
                message("a2-" + suffix, "rina-" + suffix, Direction.OUTBOUND, "2026-11-20T10:05:00Z")), false);
    }

    private void stubHistory(HistoryConversation... conversations) {
        ConversationHistoryClient client = mock(ConversationHistoryClient.class);
        when(client.conversations(any(), any())).thenAnswer(call -> List.of(conversations).iterator());
        when(clients.forChannel(Channel.MESSENGER)).thenReturn(client);
    }

    private MetaConnectedPage connectPage(String pageId) {
        Organization org = organizations.saveAndFlush(new Organization("Org " + UUID.randomUUID(),
                "org-" + UUID.randomUUID()));
        MetaConnectedPage page = new MetaConnectedPage(org, pageId);
        page.replace("Shop", cipher.encrypt("page-token"), null, Instant.now());
        return pages.saveAndFlush(page);
    }

    private int count(String table, UUID organizationId) {
        return jdbc.queryForObject("select count(*) from " + table + " where organization_id = ?", Integer.class,
                organizationId);
    }

    @Test
    void importsTwoConversationsThenARerunStoresNothingNew() {
        MetaConnectedPage page = connectPage("p-" + UUID.randomUUID());
        UUID orgId = page.getOrganization().getId();
        String suffix = UUID.randomUUID().toString();
        stubHistory(rina(suffix), rina(suffix + "-2"));

        HistoryImportResult first = imports.importHistory(Channel.MESSENGER, page.getId()).orElseThrow();
        HistoryImportResult second = imports.importHistory(Channel.MESSENGER, page.getId()).orElseThrow();

        assertEquals(new HistoryImportResult(2, 4, 0, 0, false), first);
        assertEquals(new HistoryImportResult(2, 0, 0, 4, false), second);
        assertEquals(2, count("conversations", orgId));
        assertEquals(4, count("messages", orgId));
        assertEquals(Instant.parse("2026-11-20T10:05:00Z"), jdbc.queryForObject(
                "select max(last_message_at) from conversations where organization_id = ?", java.sql.Timestamp.class,
                orgId).toInstant());
        assertEquals(2, jdbc.queryForObject("select count(*) from conversations where organization_id = ? "
                + "and meta_page_id = ?", Integer.class, orgId, page.getId()));
    }

    private static NormalizedMessage photo(String id, String peer, String text, String... urls) {
        return new NormalizedMessage(WebhookObject.PAGE, id, peer, Direction.INBOUND, text,
                Instant.parse("2026-11-20T10:00:00Z"), java.util.Arrays.stream(urls)
                        .map(url -> new NormalizedAttachment(AttachmentKind.IMAGE, url, null, null)).toList());
    }

    private String body(UUID organizationId, String externalId) {
        return jdbc.queryForObject("select body from messages where organization_id = ? and external_message_id = ?",
                String.class, organizationId, externalId);
    }

    @Test
    void aRerunReconcilesALabelledMessageThenChangesNothing() {
        MetaConnectedPage page = connectPage("p-" + UUID.randomUUID());
        UUID orgId = page.getOrganization().getId();
        String peer = "rina-" + UUID.randomUUID();
        String metaUrl = "https://cdn.example/" + UUID.randomUUID() + ".jpg";
        stubHistory(new HistoryConversation(peer, "Rina", List.of(
                photo("legacy-" + peer, peer, "[Attachment]"), photo("gone-" + peer, peer, "bye")), false));
        imports.importHistory(Channel.MESSENGER, page.getId());
        stubHistory(new HistoryConversation(peer, "Rina", List.of(
                photo("legacy-" + peer, peer, "[Photo]", metaUrl)), false));

        HistoryImportResult reconciled = imports.importHistory(Channel.MESSENGER, page.getId()).orElseThrow();
        HistoryImportResult again = imports.importHistory(Channel.MESSENGER, page.getId()).orElseThrow();

        assertEquals(new HistoryImportResult(1, 0, 1, 0, false), reconciled);
        assertEquals(new HistoryImportResult(1, 0, 0, 1, false), again);
        assertEquals("[Photo]", body(orgId, "legacy-" + peer));
        assertEquals(metaUrl, jdbc.queryForObject("select a.source_url from message_attachments a join messages m"
                + " on m.id = a.message_id where m.organization_id = ? and m.external_message_id = ?", String.class,
                orgId, "legacy-" + peer));
        assertEquals(1, count("message_attachments", orgId));
        assertEquals("bye", body(orgId, "gone-" + peer));
        assertEquals(2, count("messages", orgId));
        assertEquals(Boolean.FALSE, jdbc.queryForObject("select unread from conversations where organization_id = ?",
                Boolean.class, orgId));
        assertEquals(3, published.stream(HistoryImportedEvent.class)
                .filter(e -> e.organizationId().equals(orgId)).count());
    }

    @Test
    void aCopiedAttachmentIsNotAddedAgain() {
        MetaConnectedPage page = connectPage("p-" + UUID.randomUUID());
        UUID orgId = page.getOrganization().getId();
        String peer = "rina-" + UUID.randomUUID();
        String metaUrl = "https://cdn.example/" + UUID.randomUUID() + ".jpg";
        stubHistory(new HistoryConversation(peer, "Rina", List.of(photo("m-" + peer, peer, "[Photo]", metaUrl)),
                false));
        imports.importHistory(Channel.MESSENGER, page.getId());
        jdbc.update("update message_attachments set url = ? where organization_id = ?",
                "https://api.example.com/api/files/copy", orgId);

        HistoryImportResult result = imports.importHistory(Channel.MESSENGER, page.getId()).orElseThrow();

        assertEquals(new HistoryImportResult(1, 0, 0, 1, false), result);
        assertEquals(1, count("message_attachments", orgId));
    }

    @Test
    void aDuplicateWebhookLeavesTheStoredRowAlone() {
        MetaConnectedPage page = connectPage("p-" + UUID.randomUUID());
        UUID orgId = page.getOrganization().getId();
        String peer = "rina-" + UUID.randomUUID();
        stubHistory(new HistoryConversation(peer, "Rina", List.of(photo("m-" + peer, peer, "[Attachment]")), false));
        imports.importHistory(Channel.MESSENGER, page.getId());

        inbox.receive(page, photo("m-" + peer, peer, "[Photo]", "https://cdn.example/x.jpg"));

        assertEquals("[Attachment]", body(orgId, "m-" + peer));
        assertEquals(0, count("message_attachments", orgId));
    }

    @Test
    void reconcilingOneOrganizationNeverChangesAnothersCopy() {
        MetaConnectedPage acme = connectPage("p-" + UUID.randomUUID());
        MetaConnectedPage other = connectPage("p-" + UUID.randomUUID());
        String peer = "rina-" + UUID.randomUUID();
        stubHistory(new HistoryConversation(peer, "Rina", List.of(photo("m-" + peer, peer, "[Attachment]")), false));
        imports.importHistory(Channel.MESSENGER, acme.getId());
        imports.importHistory(Channel.MESSENGER, other.getId());
        stubHistory(new HistoryConversation(peer, "Rina", List.of(
                photo("m-" + peer, peer, "[Photo]", "https://cdn.example/" + UUID.randomUUID() + ".jpg")), false));

        imports.importHistory(Channel.MESSENGER, acme.getId());

        assertEquals("[Photo]", body(acme.getOrganization().getId(), "m-" + peer));
        assertEquals("[Attachment]", body(other.getOrganization().getId(), "m-" + peer));
        assertEquals(0, count("message_attachments", other.getOrganization().getId()));
    }

    @Test
    void theCustomerNameIsSetOnlyWhenNoneIsKnown() {
        MetaConnectedPage page = connectPage("p-" + UUID.randomUUID());
        String suffix = UUID.randomUUID().toString();
        participants.saveAndFlush(new Participant(page.getOrganization(), Channel.MESSENGER, "rina-" + suffix,
                "Known Name"));
        participants.saveAndFlush(new Participant(page.getOrganization(), Channel.MESSENGER, "rina-" + suffix + "-2",
                null));
        stubHistory(rina(suffix), rina(suffix + "-2"));

        imports.importHistory(Channel.MESSENGER, page.getId());

        assertEquals("Known Name", participants.findByOrganizationIdAndChannelAndExternalId(
                page.getOrganization().getId(), Channel.MESSENGER, "rina-" + suffix).orElseThrow().getDisplayName());
        assertEquals("Rina Akter", participants.findByOrganizationIdAndChannelAndExternalId(
                page.getOrganization().getId(), Channel.MESSENGER, "rina-" + suffix + "-2").orElseThrow()
                .getDisplayName());
    }

    @Test
    void importedMessagesPublishNoPerMessageEventAndOneSummaryEvent() {
        MetaConnectedPage page = connectPage("p-" + UUID.randomUUID());
        UUID orgId = page.getOrganization().getId();
        String suffix = UUID.randomUUID().toString();
        stubHistory(rina(suffix));

        imports.importHistory(Channel.MESSENGER, page.getId());

        assertEquals(0, published.stream(MessageReceivedEvent.class)
                .filter(e -> e.organizationId().equals(orgId)).count());
        assertEquals(1, published.stream(HistoryImportedEvent.class)
                .filter(e -> e.organizationId().equals(orgId)).count());
    }

    @Test
    void aSuccessfulImportMarksTheConnectionAndTakesItOutOfTheCatchUp() {
        MetaConnectedPage page = connectPage("p-" + UUID.randomUUID());
        stubHistory(rina(UUID.randomUUID().toString()));
        assertTrue(pendingIds().contains(page.getId()));

        imports.importHistory(Channel.MESSENGER, page.getId());

        assertNotNull(pages.findById(page.getId()).orElseThrow().getHistoryImportedAt());
        assertTrue(!pendingIds().contains(page.getId()));
    }

    @Test
    void aFailedImportLeavesTheConnectionPendingAndKeepsWhatWasStored() {
        MetaConnectedPage page = connectPage("p-" + UUID.randomUUID());
        UUID orgId = page.getOrganization().getId();
        HistoryConversation stored = rina(UUID.randomUUID().toString());
        ConversationHistoryClient client = mock(ConversationHistoryClient.class);
        when(client.conversations(any(), any())).thenAnswer(call -> new java.util.Iterator<HistoryConversation>() {
            private boolean served;

            @Override
            public boolean hasNext() {
                if (served) {
                    throw new HistoryFetchException("Meta down");
                }
                return true;
            }

            @Override
            public HistoryConversation next() {
                served = true;
                return stored;
            }
        });
        when(clients.forChannel(Channel.MESSENGER)).thenReturn(client);

        assertThrows(HistoryFetchException.class, () -> imports.importHistory(Channel.MESSENGER, page.getId()));

        assertNull(pages.findById(page.getId()).orElseThrow().getHistoryImportedAt());
        assertEquals(2, count("messages", orgId));
        assertTrue(pendingIds().contains(page.getId()));
        MetaConnectedPage failed = pages.findById(page.getId()).orElseThrow();
        assertEquals(HistoryImportFailure.FETCH, failed.getHistoryImportError());
        assertNotNull(failed.getHistoryImportFailedAt());
    }

    @Test
    void aLaterSuccessfulImportClearsTheRecordedFailure() {
        MetaConnectedPage page = connectPage("p-" + UUID.randomUUID());
        ConversationHistoryClient failing = mock(ConversationHistoryClient.class);
        when(failing.conversations(any(), any())).thenThrow(new HistoryPermissionException());
        when(clients.forChannel(Channel.MESSENGER)).thenReturn(failing);
        assertThrows(HistoryPermissionException.class, () -> imports.importHistory(Channel.MESSENGER, page.getId()));
        assertEquals(HistoryImportFailure.PERMISSION,
                pages.findById(page.getId()).orElseThrow().getHistoryImportError());

        stubHistory(rina(UUID.randomUUID().toString()));
        imports.importHistory(Channel.MESSENGER, page.getId());

        MetaConnectedPage cleared = pages.findById(page.getId()).orElseThrow();
        assertNull(cleared.getHistoryImportError());
        assertNull(cleared.getHistoryImportFailedAt());
        assertNotNull(cleared.getHistoryImportedAt());
    }

    @Test
    void theStartupCatchUpImportsOnlyConnectionsWithoutAnImportDate() throws InterruptedException {
        MetaConnectedPage pending = connectPage("p-" + UUID.randomUUID());
        MetaConnectedPage done = connectPage("p-" + UUID.randomUUID());
        done.markHistoryImported(Instant.now());
        pages.saveAndFlush(done);
        stubHistory(rina(UUID.randomUUID().toString()));

        trigger.catchUp();

        awaitMarked(pending.getId());
        assertEquals(0, count("messages", done.getOrganization().getId()));
        assertEquals(2, count("messages", pending.getOrganization().getId()));
    }

    private List<UUID> pendingIds() {
        return store.pending().stream().map(PendingHistoryImport::accountId).toList();
    }

    private void awaitMarked(UUID pageId) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            if (pages.findById(pageId).orElseThrow().getHistoryImportedAt() != null) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("catch-up did not finish in time");
    }
}
