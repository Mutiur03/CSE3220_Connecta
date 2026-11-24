package com.connecta.backend.meta.service;

import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.inbox.service.NamelessCustomer;
import java.util.Optional;

/**
 * Looks up a customer's display name on one Meta channel (Adapter): the inbox never sees Meta's API. A failed
 * lookup is not an error for the caller: it answers empty and the next inbound message tries again.
 */
public interface CustomerProfileClient {

    Channel channel();

    /**
     * The customer's name from Meta, or empty when Meta has none (no consent, permission error, timeout, no name).
     *
     * @throws ChannelNotConnectedException the conversation's account is gone or the organization has none
     * @throws ConversationNotLinkedException no recorded account and the organization has several
     */
    Optional<String> fetchName(NamelessCustomer customer);
}
