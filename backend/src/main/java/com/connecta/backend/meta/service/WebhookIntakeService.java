package com.connecta.backend.meta.service;

import com.connecta.backend.config.MetaWebhookProperties;
import com.connecta.backend.meta.domain.MetaWebhookEvent;
import com.connecta.backend.meta.domain.WebhookObject;
import com.connecta.backend.meta.repository.MetaWebhookEventRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Optional;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Receives Meta webhook deliveries (Template Method skeleton: verify, store, process). Verification and storage
 * live here; the per-product parsing and routing are in {@link WebhookEventProcessor}. A delivery is stored only
 * after its signature checks out, and deliveries for other Meta products are acknowledged without being stored.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookIntakeService {

    static final String SUBSCRIBE_MODE = "subscribe";
    static final String DELIVERIES_METER = "meta.webhook.deliveries";
    private static final String RESULT_TAG = "result";
    private static final String UNKNOWN_OBJECT = "unknown";

    private final WebhookSignatureVerifier verifier;
    private final MetaWebhookProperties properties;
    private final MetaWebhookEventRepository events;
    private final WebhookEventProcessor processor;
    private final ObjectMapper json;
    private final Clock clock;
    private final MeterRegistry meters;

    /** The challenge Meta expects back when it subscribes the webhook. Anything else is refused. */
    public String confirmSubscription(String mode, String verifyToken, String challenge) {
        boolean tokenMatches = properties.configured() && verifyToken != null
                && MessageDigest.isEqual(properties.verifyToken().getBytes(StandardCharsets.UTF_8),
                        verifyToken.getBytes(StandardCharsets.UTF_8));
        if (!SUBSCRIBE_MODE.equals(mode) || !tokenMatches) {
            throw new WebhookVerificationFailedException();
        }
        return challenge;
    }

    @Transactional
    public void receive(String rawBody, String signatureHeader) {
        JsonNode root = parse(rawBody);
        Optional<WebhookObject> object = WebhookObject.fromMetaName(root.path("object").asText());
        if (object.isEmpty()) {
            return;
        }
        verifyOrReject(object.get(), rawBody, signatureHeader);
        count("accepted");
        MetaWebhookEvent event = events.save(new MetaWebhookEvent(object.get(), rawBody, clock.instant()));
        processor.process(event);
    }

    private void verifyOrReject(WebhookObject object, String rawBody, String signatureHeader) {
        try {
            verifier.verify(object, rawBody, signatureHeader);
        } catch (InvalidWebhookSignatureException e) {
            reject(e, object.name());
            throw e;
        }
    }

    private JsonNode parse(String rawBody) {
        try {
            JsonNode root = json.readTree(rawBody);
            if (root == null || !root.isObject()) {
                throw new MalformedWebhookException();
            }
            return root;
        } catch (JacksonException | MalformedWebhookException e) {
            MalformedWebhookException malformed = new MalformedWebhookException();
            reject(malformed, UNKNOWN_OBJECT);
            throw malformed;
        }
    }

    /** One WARN line with the reason class and the object only: never the body or the signature. */
    private void reject(RuntimeException reason, String objectName) {
        log.warn("Webhook delivery rejected: {} (object {})", reason.getClass().getSimpleName(), objectName);
        count("rejected");
    }

    private void count(String result) {
        meters.counter(DELIVERIES_METER, RESULT_TAG, result).increment();
    }
}
