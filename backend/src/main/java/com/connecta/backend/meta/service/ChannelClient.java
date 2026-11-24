package com.connecta.backend.meta.service;

import com.connecta.backend.inbox.domain.Channel;
import java.util.UUID;

/**
 * Sends one text message to a customer on one Meta channel (Adapter): the inbox never sees Meta's API.
 * Receiving is not part of this contract; webhook parsing lives in the intake classes.
 */
public interface ChannelClient {

    Channel channel();

    /**
     * Sends the text with the organization's connected account for this channel and returns the provider
     * message id, which the later webhook echo carries.
     *
     * @throws ChannelNotConnectedException the organization has no connected account for this channel
     * @throws MessagingWindowClosedException Meta refused because the reply window or policy forbids it
     * @throws ChannelSendFailedException any other failure to deliver
     */
    String send(UUID organizationId, String recipientId, String text);
}
