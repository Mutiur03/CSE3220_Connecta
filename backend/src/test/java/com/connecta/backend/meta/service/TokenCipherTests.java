package com.connecta.backend.meta.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.connecta.backend.config.MetaProperties;
import java.util.Base64;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TokenCipherTests {

    /** base64 of 32 bytes: a valid AES-256 key for tests only. */
    private static final String KEY = "dGVzdC1vbmx5LW1ldGEtdG9rZW4ta2V5LTAwMDAwMDE=";
    private static final String TOKEN = "EAAGtestUserToken";

    private final TokenCipher cipher = new TokenCipher(props(KEY));

    @Nested
    class RoundTrip {

        @Test
        void decryptReturnsTheOriginalToken() {
            assertEquals(TOKEN, cipher.decrypt(cipher.encrypt(TOKEN)));
        }

        @Test
        void theStoredValueDoesNotContainThePlainToken() {
            assertFalse(cipher.encrypt(TOKEN).contains(TOKEN));
        }

        @Test
        void encryptingTheSameTokenTwiceGivesDifferentValues() {
            assertNotEquals(cipher.encrypt(TOKEN), cipher.encrypt(TOKEN));
        }
    }

    @Nested
    class Tampering {

        @Test
        void analteredValueIsRejected() {
            byte[] packed = Base64.getDecoder().decode(cipher.encrypt(TOKEN));
            packed[packed.length - 1] ^= 0x01;
            String altered = Base64.getEncoder().encodeToString(packed);

            assertThrows(TokenDecryptionException.class, () -> cipher.decrypt(altered));
        }

        @Test
        void aValueFromAnotherKeyIsRejected() {
            String encrypted = new TokenCipher(props("ZGV2LW9ubHktbWV0YS10b2tlbi1rZXktMDAwMDAwMDE=")).encrypt(TOKEN);

            assertThrows(TokenDecryptionException.class, () -> cipher.decrypt(encrypted));
        }
    }

    @Nested
    class KeyChecks {

        @Test
        void aMissingKeyStopsStartup() {
            assertThrows(IllegalStateException.class, () -> new TokenCipher(props("")));
        }

        @Test
        void aKeyThatIsNot32BytesIsRejected() {
            String sixteenBytes = Base64.getEncoder().encodeToString(new byte[16]);

            assertThrows(IllegalStateException.class, () -> new TokenCipher(props(sixteenBytes)));
        }
    }

    private static MetaProperties props(String key) {
        return new MetaProperties("app", "secret", null, "config", "https://api.example.com", key);
    }
}
