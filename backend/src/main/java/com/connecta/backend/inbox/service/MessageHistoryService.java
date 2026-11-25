package com.connecta.backend.inbox.service;

import com.connecta.backend.common.dto.PageResponse;
import com.connecta.backend.inbox.domain.Message;
import com.connecta.backend.inbox.domain.MessageAttachment;
import com.connecta.backend.inbox.dto.MessageResponse;
import com.connecta.backend.inbox.repository.MessageAttachmentRepository;
import com.connecta.backend.inbox.repository.MessageRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pages through a conversation's messages, newest first, for any member of the organization. The attachments of
 * the whole page are loaded in one extra query.
 */
@Service
@RequiredArgsConstructor
public class MessageHistoryService {

    /** Newest first, then by id so messages sent in the same instant never swap places between pages. */
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("sentAt"), Sort.Order.desc("id"));

    private final ConversationDetailService conversations;
    private final MessageRepository messages;
    private final MessageAttachmentRepository attachments;

    @Transactional(readOnly = true)
    public PageResponse<MessageResponse> list(String subject, UUID organizationId, UUID conversationId, int page,
            int size) {
        conversations.find(subject, organizationId, conversationId);
        Page<Message> found = messages.findByConversationIdAndOrganizationId(conversationId, organizationId,
                PageRequest.of(page, size, NEWEST_FIRST));
        Map<UUID, List<MessageAttachment>> files = attachments.findByMessage(organizationId,
                found.getContent().stream().map(Message::getId).toList());
        return PageResponse.from(found,
                message -> MessageResponse.from(message, files.getOrDefault(message.getId(), List.of())));
    }
}
