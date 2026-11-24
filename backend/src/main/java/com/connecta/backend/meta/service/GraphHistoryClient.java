package com.connecta.backend.meta.service;

import com.connecta.backend.config.HistoryImportProperties;
import com.connecta.backend.meta.domain.WebhookObject;
import com.connecta.backend.meta.service.NormalizedMessage.Direction;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

/**
 * Shared read step of the history adapters (Template Method): asks the Graph conversations edge for one page,
 * follows {@code paging.next} lazily and turns each conversation into a {@link HistoryConversation}. Subclasses
 * supply the host, the product and the Graph product name. Messages are oldest first in the result.
 *
 * <p>Never logs the token or a paging URL (it can carry a token). A paging URL on another host is refused.
 */
@Slf4j
abstract class GraphHistoryClient implements ConversationHistoryClient {

    static final String ATTACHMENT_LABEL = "[Attachment]";

    private static final String FIELDS = "participants,messages.limit(%d){id,message,from,created_time}";
    private static final DateTimeFormatter GRAPH_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");
    private static final int PERMISSION_CODE_DENIED = 10;
    private static final int TOKEN_CODE = 190;
    private static final int PERMISSION_CODE_FIRST = 200;
    private static final int PERMISSION_CODE_LAST = 299;

    private final HistoryImportProperties limits;
    private final RestClient graph;
    private final String host;

    protected GraphHistoryClient(RestClient.Builder restClients, String host, String graphVersion,
            HistoryImportProperties limits) {
        this.limits = limits;
        this.host = host;
        this.graph = restClients.baseUrl(host + graphVersion).build();
    }

    /** A builder with the configured connect and read timeout, for production wiring. */
    protected static RestClient.Builder timedBuilder(HistoryImportProperties limits) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(limits.requestTimeout()).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(limits.requestTimeout());
        return RestClient.builder().requestFactory(factory);
    }

    protected abstract WebhookObject object();

    /** The value of the Graph {@code platform} query parameter. */
    protected abstract String platform();

    @Override
    public Iterator<HistoryConversation> conversations(String accountExternalId, String accessToken) {
        return new Pages(accountExternalId, accessToken);
    }

    private final class Pages implements Iterator<HistoryConversation> {

        private final String accountId;
        private final String token;
        private final Deque<HistoryConversation> buffer = new ArrayDeque<>();
        private boolean started;
        private String nextUrl;

        Pages(String accountId, String token) {
            this.accountId = accountId;
            this.token = token;
        }

        @Override
        public boolean hasNext() {
            while (buffer.isEmpty() && (!started || nextUrl != null)) {
                loadPage();
            }
            return !buffer.isEmpty();
        }

        @Override
        public HistoryConversation next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            return buffer.poll();
        }

        private void loadPage() {
            JsonNode page = fetch(started ? nextRequest() : firstRequest());
            started = true;
            for (JsonNode conversation : page.path("data")) {
                parse(conversation, accountId).ifPresent(buffer::add);
            }
            String next = page.path("paging").path("next").asText("");
            nextUrl = next.isBlank() ? null : requireSameHost(next);
        }

        private RestClient.RequestHeadersSpec<?> firstRequest() {
            String fields = FIELDS.formatted(limits.messagesPerConversation());
            return graph.get().uri("/{id}/conversations?platform={platform}&fields={fields}&limit={limit}",
                    accountId, platform(), fields, limits.pageSize());
        }

        private RestClient.RequestHeadersSpec<?> nextRequest() {
            return graph.get().uri(URI.create(nextUrl));
        }

        private JsonNode fetch(RestClient.RequestHeadersSpec<?> request) {
            try {
                JsonNode body = request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .accept(MediaType.APPLICATION_JSON).retrieve().body(JsonNode.class);
                if (body == null) {
                    throw new HistoryFetchException("Meta returned an empty history response");
                }
                return body;
            } catch (RestClientResponseException e) {
                throw mapError(e);
            } catch (RestClientException e) {
                log.warn("Meta history request failed: {}", e.getClass().getSimpleName());
                throw new HistoryFetchException("Meta history request failed", e);
            }
        }
    }

    private String requireSameHost(String next) {
        if (!next.startsWith(host)) {
            throw new HistoryFetchException("Meta returned a paging link to an unexpected host");
        }
        return next;
    }

    private static RuntimeException mapError(RestClientResponseException e) {
        Integer code = graphCode(e);
        log.warn("Meta history rejected: status {}, graph code {}", e.getStatusCode().value(), code);
        if (code != null && isPermissionCode(code)) {
            return new HistoryPermissionException();
        }
        return new HistoryFetchException("Meta rejected the history request", e);
    }

    private static boolean isPermissionCode(int code) {
        return code == PERMISSION_CODE_DENIED || code == TOKEN_CODE
                || code >= PERMISSION_CODE_FIRST && code <= PERMISSION_CODE_LAST;
    }

    private static Integer graphCode(RestClientResponseException e) {
        try {
            JsonNode error = e.getResponseBodyAs(JsonNode.class);
            return error == null || !error.path("error").path("code").isNumber() ? null
                    : error.path("error").path("code").asInt();
        } catch (RestClientException unreadable) {
            return null;
        }
    }

    private Optional<HistoryConversation> parse(JsonNode conversation, String accountId) {
        JsonNode peer = peerOf(conversation.path("participants").path("data"), accountId);
        if (peer == null) {
            return Optional.empty();
        }
        String peerId = peer.path("id").asText("");
        JsonNode messageList = conversation.path("messages");
        List<NormalizedMessage> messages = new ArrayList<>();
        String name = nameOf(peer);
        for (JsonNode message : messageList.path("data")) {
            JsonNode from = message.path("from");
            boolean outbound = accountId.equals(from.path("id").asText(""));
            if (name == null && !outbound) {
                name = nameOf(from);
            }
            toMessage(message, peerId, outbound).ifPresent(messages::add);
        }
        Collections.reverse(messages);
        boolean more = !messageList.path("paging").path("next").asText("").isBlank();
        return Optional.of(new HistoryConversation(peerId, name, List.copyOf(messages), more));
    }

    private static JsonNode peerOf(JsonNode participants, String accountId) {
        for (JsonNode participant : participants) {
            String id = participant.path("id").asText("");
            if (!id.isBlank() && !id.equals(accountId)) {
                return participant;
            }
        }
        return null;
    }

    private static String nameOf(JsonNode person) {
        String name = person.path("name").asText("");
        if (!name.isBlank()) {
            return name;
        }
        String username = person.path("username").asText("");
        return username.isBlank() ? null : username;
    }

    private Optional<NormalizedMessage> toMessage(JsonNode message, String peerId, boolean outbound) {
        String id = message.path("id").asText("");
        Instant sentAt = parseTime(message.path("created_time").asText(""));
        if (id.isBlank() || sentAt == null) {
            return Optional.empty();
        }
        String text = message.path("message").asText("");
        return Optional.of(new NormalizedMessage(object(), id, peerId,
                outbound ? Direction.OUTBOUND : Direction.INBOUND, text.isBlank() ? ATTACHMENT_LABEL : text,
                sentAt));
    }

    private static Instant parseTime(String value) {
        try {
            return OffsetDateTime.parse(value, GRAPH_TIME).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
