package com.connecta.backend.inbox.dto;

import com.connecta.backend.inbox.domain.Participant;
import java.util.UUID;

/** A customer in a conversation, as the API returns it. */
public record ParticipantResponse(UUID id, String externalId, String displayName, String avatarUrl) {

    public static ParticipantResponse from(Participant participant) {
        return new ParticipantResponse(participant.getId(), participant.getExternalId(),
                participant.getDisplayName(), participant.getProfilePictureUrl());
    }
}
