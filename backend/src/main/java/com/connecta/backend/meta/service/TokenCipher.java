package com.connecta.backend.meta.service;

import com.connecta.backend.config.MetaProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Encrypts Meta tokens at rest with AES-256-GCM. Each value gets a fresh random 12-byte nonce, stored in front of
 * the ciphertext, and GCM's tag makes any change to the stored value fail on decrypt. The key comes from
 * app.meta.token-encryption-key and must be 32 bytes, base64 encoded.
 */
@Component
public class TokenCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecureRandom random = new SecureRandom();
    private final SecretKeySpec key;

    public TokenCipher(MetaProperties properties) {
        this.key = new SecretKeySpec(decodeKey(properties.tokenEncryptionKey()), "AES");
    }

    /** The token encrypted and base64 encoded as nonce followed by ciphertext. */
    public String encrypt(String plaintext) {
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] packed = new byte[NONCE_BYTES + ciphertext.length];
            System.arraycopy(nonce, 0, packed, 0, NONCE_BYTES);
            System.arraycopy(ciphertext, 0, packed, NONCE_BYTES, ciphertext.length);
            return Base64.getEncoder().encodeToString(packed);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Token encryption failed", e);
        }
    }

    /** The original token. A value that was altered or encrypted with another key raises TokenDecryptionException. */
    public String decrypt(String encrypted) {
        try {
            byte[] packed = Base64.getDecoder().decode(encrypted);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key,
                    new GCMParameterSpec(TAG_BITS, packed, 0, NONCE_BYTES));
            byte[] plaintext = cipher.doFinal(packed, NONCE_BYTES, packed.length - NONCE_BYTES);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new TokenDecryptionException(e);
        }
    }

    private static byte[] decodeKey(String base64) {
        if (base64 == null || base64.isBlank()) {
            throw new IllegalStateException("app.meta.token-encryption-key is required");
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("app.meta.token-encryption-key must be base64 encoded", e);
        }
        if (raw.length != KEY_BYTES) {
            throw new IllegalStateException("app.meta.token-encryption-key must decode to 32 bytes");
        }
        return raw;
    }
}
