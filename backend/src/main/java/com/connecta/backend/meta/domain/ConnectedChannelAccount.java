package com.connecta.backend.meta.domain;

import com.connecta.backend.inbox.domain.Conversation;
import com.connecta.backend.org.domain.Organization;

/**
 * An account an organization connected for one channel (a Facebook Page or an Instagram account). Webhook
 * routing hands it to the inbox, which links a new conversation to it without knowing which kind it is.
 */
public interface ConnectedChannelAccount {

    Organization getOrganization();

    /** Records that a webhook for this connection was processed at the given time. */
    void markWebhookReceived(java.time.Instant at);

    /** Records this account as the one the conversation belongs to. */
    void linkTo(Conversation conversation);
}
