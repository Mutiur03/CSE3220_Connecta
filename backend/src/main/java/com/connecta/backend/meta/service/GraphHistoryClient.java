package com.connecta.backend.meta.service;

import com.connecta.backend.config.HistoryImportProperties;
import com.connecta.backend.inbox.domain.AttachmentKind;
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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
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
 * <p>Each message's {@code attachments} edge is read as image, video or file data; an attachment of another
 * shape, or without an http(s) URL, is skipped and the message keeps only its label. When Graph rejects the
 * attachment fields on the first request (a 400 with code 100, not a permission or token error), that request is
 * sent once more without them and the import goes on with labels only.
 *
 * <p>A message with neither text nor attachments may still point at something: {@link #linksOf} is the hook a
 * subclass overrides to look it up with one more request (default: none). That request counts toward
 * {@code max-graph-pages} like every other, is skipped once the cap is reached, and a failure on one message is
 * logged by reason and leaves that message label-only; it never fails the import.
 *
 * <p>Never logs the token or a paging URL (it can carry a token). A paging URL on another host is refused.
 */
@Slf4j
abstract class GraphHistoryClient implements ConversationHistoryClient {

    private static final String FIELDS_WITH_ATTACHMENTS = "participants,messages.limit(%d){id,message,from,"
            + "created_time,attachments{mime_type,name,file_url,image_data{url},video_data{url}}}";
    private static final String FIELDS_WITHOUT_ATTACHMENTS =
            "participants,messages.limit(%d){id,message,from,created_time}";
    private static final int INVALID_FIELD_CODE = 100;
    private static final String URL = "url";
    private static final DateTimeFormatter GRAPH_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");
    private static final int HTTPS_PORT = 443;
    private static final int PERMISSION_CODE_DENIED = 10;
    private static final int TOKEN_CODE = 190;
    private static final int PERMISSION_CODE_FIRST = 200;
    private static final int PERMISSION_CODE_LAST = 299;

    private final HistoryImportProperties limits;
    private final RestClient graph;
    private final String hostName;

    protected GraphHistoryClient(RestClient.Builder restClients, String host, String graphVersion,
            HistoryImportProperties limits) {
        this.limits = limits;
        this.hostName = URI.create(host).getHost();
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

    /**
     * Hook: the links a message without text or attachments points at, read with {@code graph}. Default: none,
     * without a request. A Graph failure may be thrown; the caller logs it and keeps the message label-only.
     */
    protected List<NormalizedAttachment> linksOf(String messageId, GraphReader graph) {
        return List.of();
    }

    /** One Graph GET (a URI template relative to the version path), counted and mapped like every request. */
    @FunctionalInterface
    protected interface GraphReader {
        JsonNode get(String uriTemplate, Object... variables);
    }

    @Override
    public Iterator<HistoryConversation> conversations(String accountExternalId, String accessToken) {
        return conversations(accountExternalId, accessToken, HistoryBounds.of(limits));
    }

    @Override
    public Iterator<HistoryConversation> conversations(String accountExternalId, String accessToken,
            HistoryBounds bounds) {
        return new Pages(accountExternalId, accessToken, bounds);
    }

    private final class Pages implements Iterator<HistoryConversation> {

        private final String accountId;
        private final String token;
        private final Deque<HistoryConversation> buffer = new ArrayDeque<>();
        private boolean started;
        private URI nextUrl;
        private int fetchedMessages;
        /** Conversation and message pages fetched; bounded by {@code max-graph-pages}. */
        private int graphRequests;
        /** Single-message lookups (shared posts); a budget of their own so they cannot starve paging. */
        private int lookupRequests;
        private final Set<URI> followed = new HashSet<>();
        private final HistoryBounds bounds;

        Pages(String accountId, String token, HistoryBounds bounds) {
            this.accountId = accountId;
            this.token = token;
            this.bounds = bounds;
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
            JsonNode page = started ? fetch(nextRequest()) : fetchFirstPage();
            started = true;
            for (JsonNode conversation : page.path("data")) {
                parse(conversation, accountId, this).ifPresent(buffer::add);
            }
            URI next = nextLink(page);
            if (next != null && !mayFollow(next)) {
                markLastTruncated();
                next = null;
            }
            nextUrl = next;
        }

        /**
         * Guards against a paging link that repeats or never ends: more page requests than
         * {@code max-graph-pages}, or a link already followed in this import, ends the paging instead of looping.
         * Records the link.
         */
        private boolean mayFollow(URI next) {
            if (graphRequests >= limits.maxGraphPages()) {
                log.warn("Meta history paging stopped: page cap of {} reached", limits.maxGraphPages());
                return false;
            }
            if (!followed.add(next)) {
                log.warn("Meta history paging stopped: Meta repeated a paging link");
                return false;
            }
            return true;
        }

        private void markLastTruncated() {
            HistoryConversation last = buffer.pollLast();
            if (last != null) {
                buffer.addLast(new HistoryConversation(last.peerId(), last.peerName(), last.messages(), true));
            }
        }

        /** The first page with attachments, or without them when Graph rejects those fields. */
        private JsonNode fetchFirstPage() {
            try {
                return send(firstRequest(FIELDS_WITH_ATTACHMENTS));
            } catch (RestClientResponseException e) {
                if (!rejectsFields(e)) {
                    throw mapError(e);
                }
            }
            log.warn("Meta history rejected the attachment fields; importing without attachments");
            return fetch(firstRequest(FIELDS_WITHOUT_ATTACHMENTS));
        }

        private RestClient.RequestHeadersSpec<?> firstRequest(String fieldList) {
            String fields = fieldList.formatted(bounds.messagesPerConversation());
            return graph.get().uri("/{id}/conversations?platform={platform}&fields={fields}&limit={limit}",
                    accountId, platform(), fields, limits.pageSize());
        }

        private JsonNode read(String uriTemplate, Object... variables) {
            return fetch(graph.get().uri(uriTemplate, variables), true);
        }

        /** The links of a label-only message, or none when the lookup budget is spent or the lookup fails. */
        private List<NormalizedAttachment> lookUpLinks(String messageId) {
            if (lookupRequests >= limits.maxGraphPages()) {
                return List.of();
            }
            try {
                return linksOf(messageId, this::read);
            } catch (HistoryFetchException | HistoryPermissionException e) {
                log.info("Meta message lookup skipped: {}", e.getClass().getSimpleName());
                return List.of();
            }
        }

        private RestClient.RequestHeadersSpec<?> nextRequest() {
            return graph.get().uri(nextUrl);
        }

        private JsonNode fetch(RestClient.RequestHeadersSpec<?> request) {
            return fetch(request, false);
        }

        private JsonNode fetch(RestClient.RequestHeadersSpec<?> request, boolean lookup) {
            try {
                return send(request, lookup);
            } catch (RestClientResponseException e) {
                throw mapError(e);
            }
        }

        /** One Graph request. A refusal by Meta is left to the caller; any other failure is a fetch failure. */
        private JsonNode send(RestClient.RequestHeadersSpec<?> request) {
            return send(request, false);
        }

        private JsonNode send(RestClient.RequestHeadersSpec<?> request, boolean lookup) {
            if (lookup) {
                lookupRequests++;
            } else {
                graphRequests++;
            }
            try {
                JsonNode body = request.headers(GraphAuth.bearer(token)).accept(MediaType.APPLICATION_JSON)
                        .retrieve().body(JsonNode.class);
                if (body == null) {
                    throw new HistoryFetchException("Meta returned an empty history response");
                }
                return body;
            } catch (RestClientResponseException e) {
                throw e;
            } catch (RestClientException e) {
                log.warn("Meta history request failed: {}", e.getClass().getSimpleName());
                throw new HistoryFetchException("Meta history request failed", e);
            }
        }

        /**
         * The conversation's messages, newest first: the embedded page, then its {@code paging.next} pages while
         * the per-conversation and total limits allow. {@code more} is set when messages were left behind.
         */
        private List<JsonNode> messagesOf(JsonNode edge, Truncation truncation) {
            List<JsonNode> messages = new ArrayList<>();
            JsonNode page = edge;
            URI next;
            do {
                page.path("data").forEach(messages::add);
                fetchedMessages += page.path("data").size();
                next = nextLink(page);
                if (next != null && (!withinLimits(messages.size()) || !mayFollow(next))) {
                    break;
                }
                page = next == null ? page : fetch(graph.get().uri(next));
            } while (next != null);
            int keep = bounds.messagesPerConversation();
            truncation.more = next != null || messages.size() > keep;
            return messages.size() > keep ? messages.subList(0, keep) : messages;
        }

        private boolean withinLimits(int conversationMessages) {
            return conversationMessages < bounds.messagesPerConversation() && fetchedMessages < bounds.maxMessages();
        }
    }

    private static final class Truncation {
        boolean more;
    }

    private URI nextLink(JsonNode page) {
        String next = page.path("paging").path("next").asText("");
        return next.isBlank() ? null : requireSameHost(next);
    }

    /** Accepts only an https link on the configured Graph host (no userinfo, default port), minus its token. */
    private URI requireSameHost(String next) {
        URI link;
        try {
            link = URI.create(next);
        } catch (IllegalArgumentException e) {
            throw new HistoryFetchException("Meta returned an unreadable paging link");
        }
        boolean sameHost = "https".equalsIgnoreCase(link.getScheme()) && hostName.equalsIgnoreCase(link.getHost())
                && link.getUserInfo() == null && (link.getPort() == -1 || link.getPort() == HTTPS_PORT);
        if (!sameHost) {
            throw new HistoryFetchException("Meta returned a paging link to an unexpected host");
        }
        return GraphAuth.withoutToken(link);
    }

    private static RuntimeException mapError(RestClientResponseException e) {
        Integer code = graphCode(e);
        log.warn("Meta history rejected: status {}, graph code {}", e.getStatusCode().value(), code);
        if (code != null && isPermissionCode(code)) {
            return new HistoryPermissionException();
        }
        return new HistoryFetchException("Meta rejected the history request", e);
    }

    /** A 400 with Graph code 100: a field Graph does not know or support on this host. */
    private static boolean rejectsFields(RestClientResponseException e) {
        Integer code = graphCode(e);
        return e.getStatusCode().value() == HttpStatus.BAD_REQUEST.value() && code != null
                && code == INVALID_FIELD_CODE;
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

    private Optional<HistoryConversation> parse(JsonNode conversation, String accountId, Pages pages) {
        JsonNode peer = peerOf(conversation.path("participants").path("data"), accountId);
        if (peer == null) {
            return Optional.empty();
        }
        String peerId = peer.path("id").asText("");
        Truncation truncation = new Truncation();
        List<JsonNode> rawMessages = pages.messagesOf(conversation.path("messages"), truncation);
        List<NormalizedMessage> messages = new ArrayList<>();
        String name = nameOf(peer);
        for (JsonNode message : rawMessages) {
            JsonNode from = message.path("from");
            boolean outbound = accountId.equals(from.path("id").asText(""));
            if (name == null && !outbound) {
                name = nameOf(from);
            }
            toMessage(message, peerId, outbound, pages).ifPresent(messages::add);
        }
        Collections.reverse(messages);
        return Optional.of(new HistoryConversation(peerId, name, List.copyOf(messages), truncation.more));
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

    private Optional<NormalizedMessage> toMessage(JsonNode message, String peerId, boolean outbound, Pages pages) {
        String id = message.path("id").asText("");
        Instant sentAt = parseTime(message.path("created_time").asText(""));
        if (id.isBlank() || sentAt == null) {
            return Optional.empty();
        }
        List<NormalizedAttachment> attachments = attachmentsOf(message);
        String text = message.path("message").asText("");
        if (text.isBlank() && attachments.isEmpty()) {
            attachments = pages.lookUpLinks(id);
        }
        if (text.isBlank()) {
            text = attachments.isEmpty() ? AttachmentKind.FILE.label() : attachments.get(0).label();
        }
        return Optional.of(new NormalizedMessage(object(), id, peerId,
                outbound ? Direction.OUTBOUND : Direction.INBOUND, text, sentAt, attachments));
    }

    private static List<NormalizedAttachment> attachmentsOf(JsonNode message) {
        List<NormalizedAttachment> found = new ArrayList<>();
        for (JsonNode attachment : message.path("attachments").path("data")) {
            toAttachment(attachment).ifPresent(found::add);
        }
        return found;
    }

    /** Image data, then video data, then a file URL whose kind follows its MIME type. */
    private static Optional<NormalizedAttachment> toAttachment(JsonNode attachment) {
        String name = attachment.path("name").asText("");
        String mimeType = attachment.path("mime_type").asText("");
        String image = attachment.path("image_data").path(URL).asText("");
        if (!image.isBlank()) {
            return NormalizedAttachment.of(AttachmentKind.IMAGE, image, name, mimeType);
        }
        String video = attachment.path("video_data").path(URL).asText("");
        if (!video.isBlank()) {
            return NormalizedAttachment.of(AttachmentKind.VIDEO, video, name, mimeType);
        }
        return NormalizedAttachment.of(kindOfMimeType(mimeType), attachment.path("file_url").asText(""), name,
                mimeType);
    }

    private static AttachmentKind kindOfMimeType(String mimeType) {
        String type = mimeType.toLowerCase(Locale.ROOT);
        if (type.startsWith("image/")) {
            return AttachmentKind.IMAGE;
        }
        if (type.startsWith("video/")) {
            return AttachmentKind.VIDEO;
        }
        return type.startsWith("audio/") ? AttachmentKind.AUDIO : AttachmentKind.FILE;
    }

    private static Instant parseTime(String value) {
        try {
            return OffsetDateTime.parse(value, GRAPH_TIME).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
