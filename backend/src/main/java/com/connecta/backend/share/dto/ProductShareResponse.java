package com.connecta.backend.share.dto;

import com.connecta.backend.inbox.dto.MessageResponse;
import java.util.List;

/** What was sent: the image message first when it went out, then the text message. */
public record ProductShareResponse(List<MessageResponse> messages) {

    public ProductShareResponse {
        messages = List.copyOf(messages);
    }
}
