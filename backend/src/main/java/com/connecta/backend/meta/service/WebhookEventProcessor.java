package com.connecta.backend.meta.service;

import com.connecta.backend.inbox.service.IncomingMessageService;
import com.connecta.backend.meta.domain.ConnectedChannelAccount;
import com.connecta.backend.meta.domain.MetaConnectedAccount;
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
            List<ConnectedChannelAccount> targets = accountsFor(event.getObject(), entry.path("id").asText(""));
            if (targets.isEmpty()) {
                continue;
            }
            targets.forEach(account -> account.markWebhookReceived(clock.instant()));
            for (NormalizedMessage message : parser.parse(event.getObject(), entry)) {
                targets.forEach(account -> deliver(account, message));
            }
        }
        event.markProcessed(clock.instant());
    }

    private List<ConnectedChannelAccount> accountsFor(WebhookObject object, String entryId) {
        if (entryId.isBlank()) {
            return List.of();
        }
        if (object == WebhookObject.PAGE) {
            return List.copyOf(pages.findAllByPageId(entryId));
        }
        return List.copyOf(accounts.findAllByPlatformAndExternalAccountId(INSTAGRAM_PLATFORM, entryId));
    }

    private void deliver(ConnectedChannelAccount account, NormalizedMessage message) {
        Organization organization = account.getOrganization();
        if (processed.existsByOrganizationIdAndObjectAndExternalMessageId(organization.getId(), message.channel(),
                message.externalMessageId())) {
            return;
        }
        processed.save(new MetaProcessedMessage(organization, message.channel(), message.externalMessageId(),
                clock.instant()));
        inbox.receive(account, message);
    }
}
