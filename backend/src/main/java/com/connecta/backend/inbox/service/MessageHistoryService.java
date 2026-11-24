package com.connecta.backend.inbox.service;

import com.connecta.backend.common.dto.PageResponse;
import com.connecta.backend.inbox.dto.MessageResponse;
import com.connecta.backend.inbox.repository.MessageRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Pages through a conversation's messages, newest first, for any member of the organization. */
@Service
@RequiredArgsConstructor
public class MessageHistoryService {

    /** Newest first, then by id so messages sent in the same instant never swap places between pages. */
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("sentAt"), Sort.Order.desc("id"));

    private final ConversationDetailService conversations;
    private final MessageRepository messages;

    @Transactional(readOnly = true)
    public PageResponse<MessageResponse> list(String subject, UUID organizationId, UUID conversationId, int page,
            int size) {
        conversations.find(subject, organizationId, conversationId);
        return PageResponse.from(messages.findByConversationIdAndOrganizationId(conversationId, organizationId,
                PageRequest.of(page, size, NEWEST_FIRST)), MessageResponse::from);
    }
}
