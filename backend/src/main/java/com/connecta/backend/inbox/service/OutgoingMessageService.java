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
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sends an agent's reply through the conversation's channel (Adapter chosen by Factory), then stores it. The
 * message is saved only after Meta accepted it, with Meta's message id, so the webhook echo of the same message
 * is skipped by {@link IncomingMessageService}. When the echo won the race and stored the message first, that
 * row is attributed to the sending agent and returned instead of saving a duplicate. Any member (ADMIN or AGENT) may send.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutgoingMessageService {

    private final ConversationDetailService conversations;
    private final ChannelClientFactory channels;
    private final MessageRepository messages;
    private final ActiveCaller activeCaller;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    // The Meta call runs inside the transaction; moving it out is tracked in #244.
    @Transactional
    public MessageResponse send(String subject, UUID organizationId, UUID conversationId, String text) {
        Conversation conversation = conversations.find(subject, organizationId, conversationId);
        User agent = activeCaller.load(subject);
        String externalId = channels.forChannel(conversation.getChannel())
                .send(organizationId, conversation.getExternalThreadId(), text);
        Instant now = clock.instant();
        Optional<Message> echoed = messages.findByOrganizationIdAndExternalMessageId(organizationId, externalId);
        if (echoed.isPresent()) {
            log.info("The webhook echo stored a sent message first; attributing it to the sender");
            echoed.get().attributeTo(agent);
            return MessageResponse.from(echoed.get());
        }
        Message saved = messages.save(Message.outbound(conversation, agent, externalId, text, now));
        conversation.recordMessageAt(now);
        publisher.publishEvent(new MessageReceivedEvent(organizationId, conversationId, saved.getId(),
                conversation.getChannel(), saved.getDirection(), false));
        return MessageResponse.from(saved);
    }
}
