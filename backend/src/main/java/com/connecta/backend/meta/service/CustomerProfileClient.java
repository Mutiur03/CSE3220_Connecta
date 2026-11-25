package com.connecta.backend.meta.service;

import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.inbox.service.ProfileTarget;
import java.util.Optional;

/**
 * Looks up a customer's profile (name and picture) on one Meta channel (Adapter): the inbox never sees Meta's API. A failed
 * lookup is not an error for the caller: it answers empty and the next inbound message tries again.
 */
public interface CustomerProfileClient {

    Channel channel();

    /**
     * The customer's profile from Meta, or empty when the lookup failed or Meta has neither a name nor a usable
     * https picture (no consent, permission error, timeout).
     *
     * @throws ChannelNotConnectedException the conversation's account is gone or the organization has none
     * @throws ConversationNotLinkedException no recorded account and the organization has several
     */
    Optional<CustomerProfile> fetchProfile(ProfileTarget customer);
}
