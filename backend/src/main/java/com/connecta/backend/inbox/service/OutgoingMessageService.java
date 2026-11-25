package com.connecta.backend.inbox.service;

import com.connecta.backend.inbox.domain.Conversation;
import com.connecta.backend.inbox.dto.MessageResponse;
import com.connecta.backend.meta.service.ChannelClientFactory;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Sends an agent's reply through the conversation's channel (Adapter chosen by Factory), then stores it. This
 * method is deliberately not transactional: the membership check and conversation load are one short read
 * transaction, the Meta call holds no database connection, and {@link OutgoingMessageRecorder} stores the
 * message in a second short transaction, only after Meta accepted it. The row carries Meta's message id, so
 * the webhook echo of the same message is skipped by {@link IncomingMessageService}. If the echo committed
 * first, its row is attributed to the sending agent instead of saving a duplicate. Any member (ADMIN or AGENT)
 * may send.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutgoingMessageService {

    private final ConversationDetailService conversations;
    private final ChannelClientFactory channels;
    private final OutgoingMessageRecorder recorder;

    public MessageResponse send(String subject, UUID organizationId, UUID conversationId, String text) {
        Conversation conversation = conversations.find(subject, organizationId, conversationId);
        String externalId = channels.forChannel(conversation.getChannel())
                .send(conversation, text);
        try {
            return recorder.record(subject, organizationId, conversationId, externalId, text);
        } catch (DataIntegrityViolationException duplicate) {
            log.info("The webhook echo committed between the lookup and the save; attributing the stored row");
            return recorder.attributeStored(subject, organizationId, externalId).orElseThrow(() -> duplicate);
        }
    }
}
