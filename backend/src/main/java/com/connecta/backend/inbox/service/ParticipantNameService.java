package com.connecta.backend.inbox.service;

import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.inbox.domain.Participant;
import com.connecta.backend.inbox.repository.ParticipantRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Fills a customer display name from a source that knows it, never replacing a name already stored. */
@Service
@RequiredArgsConstructor
public class ParticipantNameService {

    private final ParticipantRepository participants;

    /** Sets the name when the organization customer has none. A blank name changes nothing. */
    @Transactional
    public void nameIfAbsent(UUID organizationId, Channel channel, String externalId, String name) {
        if (name == null || name.isBlank()) {
            return;
        }
        String trimmed = name.strip();
        if (trimmed.length() > Participant.DISPLAY_NAME_MAX_LENGTH) {
            trimmed = trimmed.substring(0, Participant.DISPLAY_NAME_MAX_LENGTH);
        }
        participants.nameIfAbsent(organizationId, channel, externalId, trimmed);
    }
}
