package com.connecta.backend.inbox.domain;

/** Whether the team still has work to do on a conversation. Any status may move to any other. */
public enum ConversationStatus {
    OPEN,
    CLOSED
}
