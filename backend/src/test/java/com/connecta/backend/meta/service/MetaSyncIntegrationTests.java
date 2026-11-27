package com.connecta.backend.meta.service;

import com.connecta.backend.support.SharedPostgres;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.connecta.backend.inbox.domain.Channel;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * The recent-message sync on real PostgreSQL (Flyway runs, including last_synced_at): a message present at Meta
 * but missing locally is stored by a sync, a second run stores nothing, and one failing account does not stop
 * another. The Meta boundary is stubbed. Skipped without Docker.
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false"})
@Import(TestClock.class)
@ContextConfiguration(initializers = RandomJwtSecret.class)
class MetaSyncIntegrationTests extends SharedPostgres {

    @MockitoBean
    ConversationHistoryClientFactory clients;

    @Autowired
    MetaSyncJob job;

    @Autowired
    OrganizationRepository organizations;

    @Autowired
    MetaConnectedPageRepository pages;

    @Autowired
    TokenCipher cipher;

    @Autowired
    JdbcTemplate jdbc;

    private static NormalizedMessage message(String id, String peer, String sentAt) {
        return new NormalizedMessage(WebhookObject.PAGE, id, peer, Direction.INBOUND, "text " + id,
                Instant.parse(sentAt), List.of());
    }

    private void stubHistory(HistoryConversation... conversations) {
        ConversationHistoryClient client = mock(ConversationHistoryClient.class);
        when(client.conversations(any(), any(), any())).thenAnswer(call -> List.of(conversations).iterator());
        when(clients.forChannel(Channel.MESSENGER)).thenReturn(client);
    }

    private MetaConnectedPage connectPage() {
        Organization org = organizations.saveAndFlush(new Organization("Org " + UUID.randomUUID(),
                "org-" + UUID.randomUUID()));
        MetaConnectedPage page = new MetaConnectedPage(org, "p-" + UUID.randomUUID());
        page.replace("Shop", cipher.encrypt("page-token"), null, Instant.now());
        return pages.saveAndFlush(page);
    }

    private int messages(UUID organizationId) {
        return jdbc.queryForObject("select count(*) from messages where organization_id = ?", Integer.class,
                organizationId);
    }

    @Test
    void aMissedMessageIsStoredBySyncAndASecondRunStoresNothing() {
        MetaConnectedPage page = connectPage();
        UUID orgId = page.getOrganization().getId();
        String peer = "rina-" + UUID.randomUUID();
        String known = "known-" + peer;
        String missed = "missed-" + peer;
        stubHistory(new HistoryConversation(peer, "Rina", List.of(message(known, peer, "2026-11-20T10:00:00Z")),
                false));
        job.syncAll();
        assertEquals(1, messages(orgId));
        stubHistory(new HistoryConversation(peer, "Rina", List.of(message(known, peer, "2026-11-20T10:00:00Z"),
                message(missed, peer, "2026-11-20T10:05:00Z")), false));

        job.syncAll();
        assertEquals(2, messages(orgId));
        job.syncAll();

        assertEquals(2, messages(orgId));
        MetaConnectedPage synced = pages.findById(page.getId()).orElseThrow();
        assertNotNull(synced.getLastSyncedAt());
        assertNull(synced.getHistoryImportedAt());
    }

    @Test
    void aFailingAccountIsLeftUnmarkedAndDoesNotStopTheNext() {
        MetaConnectedPage broken = connectPage();
        MetaConnectedPage healthy = connectPage();
        String peer = "karim-" + UUID.randomUUID();
        ConversationHistoryClient client = mock(ConversationHistoryClient.class);
        when(client.conversations(any(), any(), any())).thenAnswer(call -> {
            if (broken.getPageId().equals(call.getArgument(0))) {
                throw new HistoryFetchException("Meta down");
            }
            return List.of(new HistoryConversation(peer, "Karim",
                    List.of(message("m-" + peer, peer, "2026-11-20T10:00:00Z")), false)).iterator();
        });
        when(clients.forChannel(Channel.MESSENGER)).thenReturn(client);

        job.syncAll();

        assertNull(pages.findById(broken.getId()).orElseThrow().getLastSyncedAt());
        assertNotNull(pages.findById(healthy.getId()).orElseThrow().getLastSyncedAt());
        assertEquals(1, messages(healthy.getOrganization().getId()));
        assertEquals(0, messages(broken.getOrganization().getId()));
    }
}
