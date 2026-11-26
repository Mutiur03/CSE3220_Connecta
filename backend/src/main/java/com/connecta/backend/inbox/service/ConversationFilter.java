package com.connecta.backend.inbox.service;

import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.inbox.domain.ConversationStatus;

/** The optional conversation list filters; a null part is not applied. */
public record ConversationFilter(Channel channel, String participantName, ConversationStatus status,
        AssigneeScope assignee) {
}
