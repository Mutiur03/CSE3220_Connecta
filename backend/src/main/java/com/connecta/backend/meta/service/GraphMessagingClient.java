package com.connecta.backend.meta.service;

import com.connecta.backend.inbox.domain.AttachmentKind;
import com.connecta.backend.inbox.domain.Conversation;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Shared send step of the channel adapters (Template Method): builds the text or attachment body, posts it, reads
 * the provider message id and maps Graph errors to domain exceptions. Subclasses supply the request (URL, auth and
 * account) and the attachment kinds their channel delivers.
 */
@Slf4j
abstract class GraphMessagingClient implements ChannelClient, AttachmentChannelClient {

    /** Graph error codes for "outside the allowed window" (10) and "person not available" (551). */
    private static final Set<Integer> WINDOW_CLOSED_CODES = Set.of(10, 551);

    /** The Send API request for the conversation: messages URL and the token of its connected account. */
    protected abstract RestClient.RequestBodySpec messagesRequest(Conversation conversation);

    @Override
    public final String send(Conversation conversation, String text) {
        return post(messagesRequest(conversation), message(conversation, Map.of("text", text)));
    }

    @Override
    public final String sendAttachment(Conversation conversation, AttachmentKind kind, String url) {
        if (!supports(kind)) {
            throw new UnsupportedAttachmentException();
        }
        Map<String, Object> attachment = Map.of("type", graphType(kind),
                "payload", Map.of("url", url, "is_reusable", true));
        return post(messagesRequest(conversation), message(conversation, Map.of("attachment", attachment)));
    }

    private static Map<String, Object> message(Conversation conversation, Map<String, Object> message) {
        return Map.of("recipient", Map.of("id", conversation.getExternalThreadId()), "message", message);
    }

    private static String graphType(AttachmentKind kind) {
        return switch (kind) {
            case IMAGE -> "image";
            case VIDEO -> "video";
            case AUDIO -> "audio";
            case FILE -> "file";
        };
    }

    /** The fallback for a conversation without a recorded account: the organization's only account, else a refusal. */
    protected static <T> T onlyAccount(List<T> organizationAccounts) {
        if (organizationAccounts.isEmpty()) {
            throw new ChannelNotConnectedException();
        }
        if (organizationAccounts.size() > 1) {
            throw new ConversationNotLinkedException();
        }
        return organizationAccounts.get(0);
    }

    private String post(RestClient.RequestBodySpec request, Map<String, Object> body) {
        SendResponse response;
        try {
            response = request.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                    .body(body).retrieve().body(SendResponse.class);
        } catch (RestClientResponseException e) {
            throw mapError(e);
        } catch (RestClientException e) {
            log.warn("Meta send failed: {}", e.getClass().getSimpleName());
            throw new ChannelSendFailedException("Meta send failed", e);
        }
        if (response == null || response.messageId() == null || response.messageId().isBlank()) {
            throw new ChannelSendFailedException("Meta returned no message id");
        }
        return response.messageId();
    }

    private static RuntimeException mapError(RestClientResponseException e) {
        Integer code = graphCode(e);
        log.warn("Meta send rejected: status {}, graph code {}", e.getStatusCode().value(), code);
        if (code != null && WINDOW_CLOSED_CODES.contains(code)) {
            return new MessagingWindowClosedException();
        }
        return new ChannelSendFailedException("Meta rejected the message", e);
    }

    private static Integer graphCode(RestClientResponseException e) {
        try {
            GraphError error = e.getResponseBodyAs(GraphError.class);
            return error == null || error.error() == null ? null : error.error().code();
        } catch (RestClientException unreadable) {
            return null;
        }
    }

    record SendResponse(@JsonProperty("message_id") String messageId) {
    }

    record GraphError(ErrorBody error) {
    }

    record ErrorBody(Integer code) {
    }
}
