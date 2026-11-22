package com.connecta.backend.meta.service;

import com.connecta.backend.config.MetaProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Makes the OAuth state parameter tamper-proof without a server-side store. The state names the organization and
 * the admin who started the connection, and expires after {@link #STATE_TTL}. It is "payload.signature": the
 * payload is base64url of "organizationId|userId|expiresAtEpochSeconds", the signature an HMAC-SHA256 of the
 * payload keyed with the app secret. Meta returns the state unchanged, so the callback can trust it only after the
 * signature checks out.
 */
@Component
public class OAuthStateSigner {

    static final Duration STATE_TTL = Duration.ofMinutes(10);

    private static final String HMAC = "HmacSHA256";
    private static final String SEPARATOR = "|";
    private static final String DOT = ".";

    private final MetaProperties properties;

    public OAuthStateSigner(MetaProperties properties) {
        this.properties = properties;
    }

    public String issue(UUID organizationId, UUID userId, Instant now) {
        String payload = organizationId + SEPARATOR + userId + SEPARATOR + now.plus(STATE_TTL).getEpochSecond();
        String encodedPayload = encode(payload.getBytes(StandardCharsets.UTF_8));
        return encodedPayload + DOT + encode(sign(encodedPayload));
    }

    /** The organization and user the state was issued for. A bad signature, a malformed value or an expired state fails. */
    public OAuthState verify(String state, Instant now) {
        String[] parts = state == null ? new String[0] : state.split("\\.", -1);
        if (parts.length != 2 || !MessageDigest.isEqual(
                sign(parts[0]), decodeOrEmpty(parts[1]))) {
            throw new InvalidOAuthStateException();
        }
        String[] fields = new String(decodeOrEmpty(parts[0]), StandardCharsets.UTF_8).split("\\|", -1);
        if (fields.length != 3) {
            throw new InvalidOAuthStateException();
        }
        try {
            long expiresAt = Long.parseLong(fields[2]);
            if (now.getEpochSecond() >= expiresAt) {
                throw new InvalidOAuthStateException();
            }
            return new OAuthState(UUID.fromString(fields[0]), UUID.fromString(fields[1]));
        } catch (IllegalArgumentException e) {
            throw new InvalidOAuthStateException();
        }
    }

    /**
     * The organization a state names, read without checking its signature. Used only to choose which page the
     * browser is sent to after a failed callback; nothing is granted on this value.
     */
    public Optional<UUID> organizationIdOf(String state) {
        String[] parts = state == null ? new String[0] : state.split("\\.", -1);
        if (parts.length != 2) {
            return Optional.empty();
        }
        String[] fields = new String(decodeOrEmpty(parts[0]), StandardCharsets.UTF_8).split("\\|", -1);
        if (fields.length != 3) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(fields[0]));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** The organization and the admin named by a verified state. */
    public record OAuthState(UUID organizationId, UUID userId) {
    }

    private byte[] sign(String encodedPayload) {
        return sign(encodedPayload.getBytes(StandardCharsets.UTF_8));
    }

    private byte[] sign(byte[] bytes) {
        String secret = properties.appSecret();
        if (secret == null || secret.isBlank()) {
            throw new MetaNotConfiguredException();
        }
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC));
            return mac.doFinal(bytes);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("OAuth state signing failed", e);
        }
    }

    private static byte[] decodeOrEmpty(String encoded) {
        try {
            return Base64.getUrlDecoder().decode(encoded);
        } catch (IllegalArgumentException e) {
            return new byte[0];
        }
    }

    private static String encode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
