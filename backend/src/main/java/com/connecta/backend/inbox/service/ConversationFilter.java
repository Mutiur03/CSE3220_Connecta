package com.connecta.backend.inbox.service;

import com.connecta.backend.inbox.domain.Channel;

/** The optional conversation list filters; a null part is not applied. */
public record ConversationFilter(Channel channel, String participantName) {
}
