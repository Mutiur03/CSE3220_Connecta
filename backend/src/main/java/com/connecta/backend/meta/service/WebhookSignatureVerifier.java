package com.connecta.backend.meta.service;

import com.connecta.backend.config.InstagramProperties;
import com.connecta.backend.config.MetaProperties;
import com.connecta.backend.meta.domain.WebhookObject;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Checks Meta's X-Hub-Signature-256 over the raw webhook body. Each product signs with its own app secret: the
 * Facebook app for Pages, the Instagram app for Instagram, so one secret never validates the other product's
 * events. A missing secret fails closed: the event is refused, never accepted unchecked.
 */
@Component
public class WebhookSignatureVerifier {

    private static final String PREFIX = "sha256=";
    private static final String HMAC = "HmacSHA256";

    private final MetaProperties meta;
    private final InstagramProperties instagram;

    public WebhookSignatureVerifier(MetaProperties meta, InstagramProperties instagram) {
        this.meta = meta;
        this.instagram = instagram;
    }

    /** Accepts the delivery only when its signature matches the body under the object's secret. */
    public void verify(WebhookObject object, String rawBody, String signatureHeader) {
        String secret = secretFor(object);
        if (signatureHeader == null || !signatureHeader.startsWith(PREFIX)) {
            throw new InvalidWebhookSignatureException();
        }
        byte[] actual = decodeHex(signatureHeader.substring(PREFIX.length()));
        byte[] expected = hmac(secret, rawBody);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new InvalidWebhookSignatureException();
        }
    }

    private String secretFor(WebhookObject object) {
        String secret = object == WebhookObject.PAGE ? meta.appSecret() : instagram.appSecret();
        if (secret == null || secret.isBlank()) {
            throw new MetaNotConfiguredException();
        }
        return secret;
    }

    private static byte[] hmac(String secret, String body) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC));
            return mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Webhook signature check failed", e);
        }
    }

    private static byte[] decodeHex(String hex) {
        try {
            return HexFormat.of().parseHex(hex);
        } catch (IllegalArgumentException e) {
            throw new InvalidWebhookSignatureException();
        }
    }
}
