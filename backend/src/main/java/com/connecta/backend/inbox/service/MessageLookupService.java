package com.connecta.backend.inbox.service;

import com.connecta.backend.inbox.dto.MessageResponse;
import com.connecta.backend.inbox.repository.MessageAttachmentRepository;
import com.connecta.backend.inbox.repository.MessageRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads one stored message for features that react to an event (no caller: the event is trusted). */
@Service
@RequiredArgsConstructor
public class MessageLookupService {

    private final MessageRepository messages;
    private final MessageAttachmentRepository attachments;

    /** The message as the API shows it, with its attachments, only when it belongs to the organization. */
    @Transactional(readOnly = true)
    public Optional<MessageResponse> find(UUID organizationId, UUID messageId) {
        return messages.findByIdAndOrganizationId(messageId, organizationId)
                .map(message -> MessageResponse.from(message, attachments.findByMessage(organizationId,
                        List.of(messageId)).getOrDefault(messageId, List.of())));
    }
}
