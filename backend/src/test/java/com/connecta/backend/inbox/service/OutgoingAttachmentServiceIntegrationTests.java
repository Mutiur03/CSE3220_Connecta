package com.connecta.backend.inbox.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.connecta.backend.file.service.StoredFileContent;
import com.connecta.backend.file.service.StoredFileNotFoundException;
import com.connecta.backend.file.service.StoredFileService;
import com.connecta.backend.inbox.domain.AttachmentKind;
import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.inbox.domain.Conversation;
import com.connecta.backend.inbox.dto.AttachmentResponse;
import com.connecta.backend.inbox.dto.MessageResponse;
import com.connecta.backend.inbox.repository.ConversationParticipantRepository;
import com.connecta.backend.inbox.repository.ConversationRepository;
import com.connecta.backend.inbox.repository.MessageAttachmentRepository;
import com.connecta.backend.inbox.repository.MessageRepository;
import com.connecta.backend.inbox.repository.ParticipantRepository;
import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.org.repository.MembershipRepository;
import com.connecta.backend.org.repository.OrganizationRepository;
import com.connecta.backend.support.InboxFixtures;
import com.connecta.backend.support.RandomJwtSecret;
import com.connecta.backend.support.StubChannelClients;
import com.connecta.backend.support.TestClock;
import com.connecta.backend.user.domain.Roles;
import com.connecta.backend.user.domain.User;
import com.connecta.backend.user.repository.RoleRepository;
import com.connecta.backend.user.repository.UserRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Real PostgreSQL and the real proxies: the file is kept in stored_files (bytea), Meta is called with its public
 * URL outside a transaction, and the message is stored with its attachment row. A second organization cannot
 * delete the file. Skipped when Docker is absent.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false",
        "app.public-base-url=https://api.example.com/"})
@Import({TestClock.class, StubChannelClients.class})
@ContextConfiguration(initializers = RandomJwtSecret.class)
class OutgoingAttachmentServiceIntegrationTests {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 7, 7};

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    OutgoingAttachmentService service;

    @Autowired
    StoredFileService files;

    @Autowired
    StubChannelClients meta;

    @Autowired
    UserRepository users;

    @Autowired
    RoleRepository roles;

    @Autowired
    OrganizationRepository organizations;

    @Autowired
    MembershipRepository memberships;

    @Autowired
    ParticipantRepository participants;

    @Autowired
    ConversationRepository conversations;

    @Autowired
    ConversationParticipantRepository links;

    @Autowired
    MessageRepository messages;

    @Autowired
    MessageAttachmentRepository attachments;

    @BeforeEach
    void clean() {
        meta.reset();
    }

    @Test
    void storesTheFileSendsItsPublicUrlAndRecordsTheMessageWithItsAttachment() {
        InboxFixtures fixtures = new InboxFixtures(users, roles, organizations, memberships, participants,
                conversations, links, messages);
        User agent = fixtures.user(Roles.AGENT);
        Organization acme = fixtures.organization("Acme");
        Organization other = fixtures.organization("Other");
        fixtures.join(acme, agent, Roles.AGENT);
        Conversation conversation = fixtures.conversation(acme, Channel.MESSENGER, "Ann", "Hi", TestClock.NOW);

        MessageResponse sent = service.send(agent.getId().toString(), acme.getId(), conversation.getId(),
                new AttachmentUpload(PNG, "image/png", "cat.png"));

        assertEquals("[Photo]", sent.text());
        assertEquals(agent.getId(), sent.senderId());
        AttachmentResponse attachment = sent.attachments().get(0);
        assertEquals(AttachmentKind.IMAGE, attachment.kind());
        assertTrue(attachment.url().startsWith("https://api.example.com/api/files/"), attachment.url());
        assertEquals(List.of(attachment.url()), meta.attachmentUrls());
        assertEquals(List.of(false), meta.transactionDuringSends());
        assertEquals(Set.of(sent.id()),
                attachments.findByMessage(acme.getId(), List.of(sent.id())).keySet());

        UUID fileId = UUID.fromString(attachment.url().substring(attachment.url().lastIndexOf('/') + 1));
        StoredFileContent stored = files.load(fileId);
        assertArrayEquals(PNG, stored.bytes());
        assertEquals("image/png", stored.contentType());
        assertEquals("cat.png", stored.fileName());

        files.delete(other.getId(), fileId);
        assertArrayEquals(PNG, files.load(fileId).bytes());
        files.delete(acme.getId(), fileId);
        assertThrows(StoredFileNotFoundException.class, () -> files.load(fileId));
    }
}
