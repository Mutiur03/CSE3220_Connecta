package com.connecta.backend.meta.service;

import com.connecta.backend.inbox.domain.AttachmentKind;
import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.inbox.domain.Conversation;

/**
 * Sends one attachment to a customer on one Meta channel (Adapter). Kept apart from {@link ChannelClient} so a
 * text-only channel never has to carry it. Each channel says which kinds it can deliver.
 */
public interface AttachmentChannelClient {

    Channel channel();

    /** Whether this channel can deliver the kind (Instagram cannot deliver a generic file). */
    boolean supports(AttachmentKind kind);

    /**
     * Sends the file Meta fetches from {@code url} to the conversation's customer and returns the provider
     * message id. The account rules and failures are the same as {@link ChannelClient#send}.
     *
     * @throws UnsupportedAttachmentException the channel cannot deliver this kind
     * @throws ChannelNotConnectedException the conversation's account is gone or the organization has none
     * @throws ConversationNotLinkedException no recorded account and the organization has several
     * @throws MessagingWindowClosedException Meta refused because the reply window or policy forbids it
     * @throws ChannelSendFailedException any other failure to deliver
     */
    String sendAttachment(Conversation conversation, AttachmentKind kind, String url);
}
