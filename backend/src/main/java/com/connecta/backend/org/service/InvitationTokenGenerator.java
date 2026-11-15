package com.connecta.backend.org.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * Makes the random, URL-safe token that goes into an invitation link, and its SHA-256 hash. The raw token
 * is handed to the caller once; only the hash is ever stored or compared.
 */
@Component
public class InvitationTokenGenerator {

    /** A raw token and its hash, as one value. */
    public record Issued(String raw, String hash) {
    }

    private static final int TOKEN_BYTES = 32;
    private static final String HASH_ALGORITHM = "SHA-256";

    private final SecureRandom random = new SecureRandom();

    public Issued issue() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new Issued(raw, hash(raw));
    }

    /** The lower-case hex SHA-256 of a raw token: 64 characters, the length of the token_hash column. */
    public String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance(HASH_ALGORITHM).digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
