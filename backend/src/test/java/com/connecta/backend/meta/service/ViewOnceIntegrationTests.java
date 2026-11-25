package com.connecta.backend.meta.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.connecta.backend.meta.domain.MetaConnectedAccount;
import com.connecta.backend.meta.domain.MetaWebhookEvent;
import com.connecta.backend.meta.domain.WebhookObject;
import com.connecta.backend.meta.repository.MetaConnectedAccountRepository;
import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.org.repository.OrganizationRepository;
import com.connecta.backend.support.RandomJwtSecret;
import com.connecta.backend.support.TestClock;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * View-once photos end to end on real PostgreSQL with the switch on (Flyway creates meta_view_once_marks): the echo
 * before the photo, and the photo before the echo. In both orders the stored message is the note and no attachment
 * row remains. Skipped without Docker.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false",
        "app.meta.view-once.hide=true"})
@Import(TestClock.class)
@ContextConfiguration(initializers = RandomJwtSecret.class)
class ViewOnceIntegrationTests {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    WebhookEventProcessor processor;

    @Autowired
    OrganizationRepository organizations;

    @Autowired
    MetaConnectedAccountRepository accounts;

    @Autowired
    TokenCipher cipher;

    @Autowired
    JdbcTemplate jdbc;

    private UUID connect(String accountId) {
        Organization org = organizations.saveAndFlush(new Organization("Org " + UUID.randomUUID(),
                "org-" + UUID.randomUUID()));
        MetaConnectedAccount account = new MetaConnectedAccount(org, MetaConnectedAccount.PLATFORM_INSTAGRAM,
                accountId);
        account.refresh("shop", cipher.encrypt("token"), null, Instant.now());
        accounts.saveAndFlush(account);
        return org.getId();
    }

    private void deliver(String accountId, String messaging) {
        String payload = "{\"object\":\"instagram\",\"entry\":[{\"id\":\"" + accountId + "\",\"messaging\":["
                + messaging + "]}]}";
        processor.process(new MetaWebhookEvent(WebhookObject.INSTAGRAM, payload, TestClock.NOW));
    }

    private static String echo(long ts, String mid) {
        return "{\"sender\":{\"id\":\"s\"},\"recipient\":{\"id\":\"r\"},\"timestamp\":" + ts
                + ",\"message\":{\"mid\":\"" + mid + "\",\"is_echo\":true,\"attachments\":["
                + "{\"type\":\"ephemeral\",\"payload\":{}}]}}";
    }

    private static String photo(long ts, String mid, String peer) {
        return "{\"sender\":{\"id\":\"" + peer + "\"},\"timestamp\":" + ts + ",\"message\":{\"mid\":\"" + mid
                + "\",\"attachments\":[{\"type\":\"image\",\"payload\":{\"url\":\"https://cdn.example/a.jpg\"}}]}}";
    }

    private String body(UUID orgId, String mid) {
        return jdbc.queryForObject("select body from messages where organization_id = ? and external_message_id = ?",
                String.class, orgId, mid);
    }

    private int attachments(UUID orgId) {
        return jdbc.queryForObject("select count(*) from message_attachments where organization_id = ?",
                Integer.class, orgId);
    }

    @Test
    void echoFirstStoresTheReceivedPhotoAsTheNoteWithNoAttachment() {
        String account = "ig-" + UUID.randomUUID();
        UUID org = connect(account);
        long ts = 1_700_000_100_000L + (long) (Math.random() * 1_000_000);
        String mid = "photo-" + UUID.randomUUID();

        deliver("ig-unconnected-" + UUID.randomUUID(), echo(ts, "echo-" + UUID.randomUUID()));
        deliver(account, photo(ts, mid, "peer-1"));

        assertEquals("[Disappearing photo]", body(org, mid));
        assertEquals(0, attachments(org));
    }

    @Test
    void photoFirstIsConvertedWhenTheEchoArrivesForTheSameOrganization() {
        String account = "ig-" + UUID.randomUUID();
        UUID org = connect(account);
        long ts = 1_700_000_100_000L + (long) (Math.random() * 1_000_000);
        String mid = "photo-" + UUID.randomUUID();

        deliver(account, photo(ts, mid, "peer-2"));
        assertEquals("[Photo]", body(org, mid));
        assertEquals(1, attachments(org));
        deliver(account, echo(ts, "echo-" + UUID.randomUUID()));

        assertEquals("[Disappearing photo]", body(org, mid));
        assertEquals(0, attachments(org));
    }

    @Test
    void aPhotoWithoutAMarkIsKept() {
        String account = "ig-" + UUID.randomUUID();
        UUID org = connect(account);
        String mid = "photo-" + UUID.randomUUID();

        deliver(account, photo(1_800_000_000_000L + (long) (Math.random() * 1_000_000), mid, "peer-3"));

        assertEquals("[Photo]", body(org, mid));
        assertEquals(1, attachments(org));
    }
}
