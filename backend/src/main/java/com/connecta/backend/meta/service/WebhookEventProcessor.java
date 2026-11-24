package com.connecta.backend.meta.service;

import com.connecta.backend.inbox.service.IncomingMessageService;
import com.connecta.backend.meta.domain.MetaConnectedAccount;
import com.connecta.backend.meta.domain.MetaConnectedPage;
import com.connecta.backend.meta.domain.MetaProcessedMessage;
import com.connecta.backend.meta.domain.MetaWebhookEvent;
import com.connecta.backend.meta.domain.WebhookObject;
import com.connecta.backend.meta.repository.MetaConnectedAccountRepository;
import com.connecta.backend.meta.repository.MetaConnectedPageRepository;
import com.connecta.backend.meta.repository.MetaProcessedMessageRepository;
import com.connecta.backend.org.domain.Organization;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Turns a stored webhook event into messages for the organizations that own the Page or account it names. Each
 * message is delivered once per organization: a message already recorded is skipped, so Meta's retries never
 * produce a second message. A new message is handed to the inbox, which stores and announces it. Routing: the entry id is a Page id (Messenger) or an Instagram account id, looked up
 * in the connection tables; an id no organization connected is ignored.
 */
@Service
@RequiredArgsConstructor
public class WebhookEventProcessor {

    private static final String INSTAGRAM_PLATFORM = MetaConnectedAccount.PLATFORM_INSTAGRAM;

    private final MetaConnectedPageRepository pages;
    private final MetaConnectedAccountRepository accounts;
    private final MetaProcessedMessageRepository processed;
    private final MessagingEventParser parser;
    private final IncomingMessageService inbox;
    private final ObjectMapper json;
    private final Clock clock;

    @Transactional
    public void process(MetaWebhookEvent event) {
        JsonNode root = json.readTree(event.getPayload());
        for (JsonNode entry : root.path("entry")) {
            List<Organization> organizations = organizationsFor(event.getObject(), entry.path("id").asText(""));
            if (organizations.isEmpty()) {
                continue;
            }
            for (NormalizedMessage message : parser.parse(event.getObject(), entry)) {
                organizations.forEach(organization -> deliver(organization, message));
            }
        }
        event.markProcessed(clock.instant());
    }

    private List<Organization> organizationsFor(WebhookObject object, String entryId) {
        if (entryId.isBlank()) {
            return List.of();
        }
        if (object == WebhookObject.PAGE) {
            return pages.findAllByPageId(entryId).stream().map(MetaConnectedPage::getOrganization).toList();
        }
        return accounts.findAllByPlatformAndExternalAccountId(INSTAGRAM_PLATFORM, entryId).stream()
                .map(MetaConnectedAccount::getOrganization).toList();
    }

    private void deliver(Organization organization, NormalizedMessage message) {
        if (processed.existsByOrganizationIdAndObjectAndExternalMessageId(organization.getId(), message.channel(),
                message.externalMessageId())) {
            return;
        }
        processed.save(new MetaProcessedMessage(organization, message.channel(), message.externalMessageId(),
                clock.instant()));
        inbox.receive(organization, message);
    }
}
