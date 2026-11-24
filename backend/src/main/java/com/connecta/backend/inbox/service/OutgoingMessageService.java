package com.connecta.backend.inbox.service;

import com.connecta.backend.inbox.domain.Conversation;
import com.connecta.backend.inbox.domain.Message;
import com.connecta.backend.inbox.dto.MessageResponse;
import com.connecta.backend.inbox.repository.MessageRepository;
import com.connecta.backend.meta.service.ChannelClientFactory;
import com.connecta.backend.org.service.ActiveCaller;
import com.connecta.backend.user.domain.User;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sends an agent's reply through the conversation's channel (Adapter chosen by Factory), then stores it. The
 * message is saved only after Meta accepted it, with Meta's message id, so the webhook echo of the same message
 * is skipped by {@link IncomingMessageService}. Any member (ADMIN or AGENT) may send.
 */
@Service
@RequiredArgsConstructor
public class OutgoingMessageService {

    private final ConversationDetailService conversations;
    private final ChannelClientFactory channels;
    private final MessageRepository messages;
    private final ActiveCaller activeCaller;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    // ponytail: the Meta call runs inside the transaction; move it out if send latency ever holds connections.
    @Transactional
    public MessageResponse send(String subject, UUID organizationId, UUID conversationId, String text) {
        Conversation conversation = conversations.find(subject, organizationId, conversationId);
        User agent = activeCaller.load(subject);
        String externalId = channels.forChannel(conversation.getChannel())
                .send(organizationId, conversation.getExternalThreadId(), text);
        Instant now = clock.instant();
        Message saved = messages.save(Message.outbound(conversation, agent, externalId, text, now));
        conversation.recordMessageAt(now);
        publisher.publishEvent(new MessageReceivedEvent(organizationId, conversationId, saved.getId(),
                conversation.getChannel(), saved.getDirection(), false));
        return MessageResponse.from(saved);
    }
}
