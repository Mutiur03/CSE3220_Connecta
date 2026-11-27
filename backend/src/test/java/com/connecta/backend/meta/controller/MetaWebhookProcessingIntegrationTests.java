package com.connecta.backend.meta.controller;

import com.connecta.backend.support.SharedPostgres;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.connecta.backend.meta.domain.MetaConnectedPage;
import com.connecta.backend.meta.domain.MetaWebhookEvent;
import com.connecta.backend.meta.domain.WebhookObject;
import com.connecta.backend.meta.repository.MetaConnectedPageRepository;
import com.connecta.backend.meta.repository.MetaProcessedMessageRepository;
import com.connecta.backend.meta.repository.MetaWebhookEventRepository;
import com.connecta.backend.inbox.service.MessageReceivedEvent;
import com.connecta.backend.meta.service.TokenCipher;
import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.org.repository.OrganizationRepository;
import com.connecta.backend.support.RandomJwtSecret;
import com.connecta.backend.support.TestClock;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A signed webhook delivery, end to end on real PostgreSQL: routed to the organization that connected the Page,
 * recorded so a redelivery adds nothing, and ignored when no organization connected the Page. Each test uses its
 * own Page id, so the shared database does not couple them. Skipped without Docker.
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false"})
@AutoConfigureMockMvc
@Import(TestClock.class)
@ContextConfiguration(initializers = RandomJwtSecret.class)
@RecordApplicationEvents
class MetaWebhookProcessingIntegrationTests extends SharedPostgres {

    private static final String PATH = "/api/meta/webhooks";
    private static final String APP_SECRET = "test-app-secret";

    @Autowired
    MockMvc mvc;

    @Autowired
    ApplicationEvents published;

    @Autowired
    OrganizationRepository organizations;

    @Autowired
    MetaConnectedPageRepository pages;

    @Autowired
    MetaWebhookEventRepository events;

    @Autowired
    MetaProcessedMessageRepository processed;

    @Autowired
    TokenCipher cipher;

    private static String sign(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(APP_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String pagePayload(String pageId, String mid) {
        return "{\"object\":\"page\",\"entry\":[{\"id\":\"" + pageId + "\",\"messaging\":[{\"sender\":{\"id\":\"customer\"},"
                + "\"message\":{\"mid\":\"" + mid + "\",\"text\":\"Hello\"}}]}]}";
    }

    private Organization organization() {
        return organizations.saveAndFlush(new Organization("Org " + UUID.randomUUID(), "org-" + UUID.randomUUID()));
    }

    private void connectPage(Organization organization, String pageId) {
        MetaConnectedPage page = new MetaConnectedPage(organization, pageId);
        page.replace("Shop " + pageId, cipher.encrypt("page-token"), null, Instant.now());
        pages.saveAndFlush(page);
    }

    private List<MessageReceivedEvent> deliveredMessages() {
        return published.stream(MessageReceivedEvent.class).toList();
    }

    @Test
    void aSignedMessageForAConnectedPageIsDeliveredOnceToItsOrganization() throws Exception {
        Organization acme = organization();
        String pageId = "p" + UUID.randomUUID();
        connectPage(acme, pageId);
        String body = pagePayload(pageId, "mid-" + UUID.randomUUID());

        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("X-Hub-Signature-256", sign(body)))
                .andExpect(status().isOk());

        List<MessageReceivedEvent> delivered = deliveredMessages().stream()
                .filter(e -> e.organizationId().equals(acme.getId())).toList();
        assertEquals(1, delivered.size());
        assertEquals(acme.getId(), delivered.get(0).organizationId());
        assertTrue(delivered.get(0).newConversation());
        MetaWebhookEvent stored = events.findAll().stream()
                .filter(e -> e.getPayload().equals(body)).findFirst().orElseThrow();
        assertNotNull(stored.getProcessedAt());
        assertEquals(WebhookObject.PAGE, stored.getObject());
    }

    @Test
    void aRedeliveredMessageIsNotDeliveredAgain() throws Exception {
        Organization acme = organization();
        String pageId = "p" + UUID.randomUUID();
        connectPage(acme, pageId);
        String body = pagePayload(pageId, "mid-" + UUID.randomUUID());

        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body)
                            .header("X-Hub-Signature-256", sign(body)))
                    .andExpect(status().isOk());
        }

        long deliveriesForThisPage = deliveredMessages().stream()
                .filter(e -> e.organizationId().equals(acme.getId())).count();
        assertEquals(1, deliveriesForThisPage);
        assertEquals(1, processed.findAll().stream()
                .filter(p -> p.getOrganization().getId().equals(acme.getId())).count());
    }

    @Test
    void aPageNoOrganizationConnectedDeliversNothingButIsStillStored() throws Exception {
        String unknownPage = "p-unknown-" + UUID.randomUUID();
        String body = pagePayload(unknownPage, "mid-" + UUID.randomUUID());
        int before = deliveredMessages().size();

        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("X-Hub-Signature-256", sign(body)))
                .andExpect(status().isOk());

        assertEquals(before, deliveredMessages().size());
        MetaWebhookEvent stored = events.findAll().stream()
                .filter(e -> e.getPayload().equals(body)).findFirst().orElseThrow();
        assertNotNull(stored.getProcessedAt());
    }

    @Test
    void aMessageIsRoutedOnlyToTheOrganizationThatConnectedThePage() throws Exception {
        Organization connected = organization();
        Organization unrelated = organization();
        String pageId = "p" + UUID.randomUUID();
        connectPage(connected, pageId);
        String body = pagePayload(pageId, "mid-" + UUID.randomUUID());

        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("X-Hub-Signature-256", sign(body)))
                .andExpect(status().isOk());

        assertFalse(deliveredMessages().stream()
                .anyMatch(e -> e.organizationId().equals(unrelated.getId())));
    }
}
