package com.connecta.backend.meta.service;

import com.connecta.backend.config.MetaWebhookProperties;
import com.connecta.backend.meta.domain.MetaWebhookEvent;
import com.connecta.backend.meta.domain.WebhookObject;
import com.connecta.backend.meta.repository.MetaWebhookEventRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Receives Meta webhook deliveries (Template Method skeleton: verify, parse, persist). Verification and
 * persistence live here; the per-product parsing and processing come in META-06. A delivery is stored only
 * after its signature checks out, and deliveries for other Meta products are acknowledged without being stored.
 */
@Service
@RequiredArgsConstructor
public class WebhookIntakeService {

    static final String SUBSCRIBE_MODE = "subscribe";

    private final WebhookSignatureVerifier verifier;
    private final MetaWebhookProperties properties;
    private final MetaWebhookEventRepository events;
    private final ObjectMapper json;
    private final Clock clock;

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
        verifier.verify(object.get(), rawBody, signatureHeader);
        events.save(new MetaWebhookEvent(object.get(), rawBody, clock.instant()));
    }

    private JsonNode parse(String rawBody) {
        try {
            JsonNode root = json.readTree(rawBody);
            if (root == null || !root.isObject()) {
                throw new MalformedWebhookException();
            }
            return root;
        } catch (JacksonException e) {
            throw new MalformedWebhookException();
        }
    }
}
