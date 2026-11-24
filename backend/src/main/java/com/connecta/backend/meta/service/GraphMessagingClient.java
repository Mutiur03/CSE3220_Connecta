package com.connecta.backend.meta.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Shared send step of the channel adapters (Template Method): posts the message body, reads the provider
 * message id and maps Graph errors to domain exceptions. Subclasses supply the URL, the auth and the account.
 */
@Slf4j
abstract class GraphMessagingClient implements ChannelClient {

    /** Graph error codes for "outside the allowed window" (10) and "person not available" (551). */
    private static final Set<Integer> WINDOW_CLOSED_CODES = Set.of(10, 551);

    protected static Map<String, Object> payload(String recipientId, String text) {
        return Map.of("recipient", Map.of("id", recipientId), "message", Map.of("text", text));
    }

    protected String post(RestClient.RequestBodySpec request, Map<String, Object> body) {
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
