package com.connecta.backend.meta.service;

import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.inbox.domain.Conversation;

/**
 * Sends one text message to a customer on one Meta channel (Adapter): the inbox never sees Meta's API.
 * Receiving is not part of this contract; webhook parsing lives in the intake classes.
 */
public interface ChannelClient {

    Channel channel();

    /**
     * Sends the text to the conversation's customer from the connected account the conversation belongs to and
     * returns the provider message id, which the later webhook echo carries. A conversation without a recorded
     * account (older rows, or its account was disconnected) falls back to the organization's account only when
     * it has exactly one.
     *
     * @throws ChannelNotConnectedException the conversation's account is gone or the organization has none
     * @throws ConversationNotLinkedException no recorded account and the organization has several
     * @throws MessagingWindowClosedException Meta refused because the reply window or policy forbids it
     * @throws ChannelSendFailedException any other failure to deliver
     */
    String send(Conversation conversation, String text);
}
