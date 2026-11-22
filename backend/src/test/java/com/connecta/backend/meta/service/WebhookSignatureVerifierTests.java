package com.connecta.backend.meta.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.connecta.backend.config.InstagramProperties;
import com.connecta.backend.config.MetaProperties;
import com.connecta.backend.meta.domain.WebhookObject;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class WebhookSignatureVerifierTests {

    private static final String PAGE_SECRET = "page-app-secret";
    private static final String INSTAGRAM_SECRET = "instagram-app-secret";
    private static final String BODY = "{\"object\":\"page\",\"entry\":[]}";

    private final WebhookSignatureVerifier verifier = new WebhookSignatureVerifier(
            new MetaProperties("app", PAGE_SECRET, null, "config", "https://api.example.com", "unused"),
            new InstagramProperties("ig-app", INSTAGRAM_SECRET));

    private static String sign(String secret, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    @Nested
    class Accepts {

        @Test
        void aPageEventSignedWithTheFacebookAppSecret() {
            assertDoesNotThrow(() -> verifier.verify(WebhookObject.PAGE, BODY, sign(PAGE_SECRET, BODY)));
        }

        @Test
        void anInstagramEventSignedWithTheInstagramAppSecret() {
            assertDoesNotThrow(() -> verifier.verify(WebhookObject.INSTAGRAM, BODY, sign(INSTAGRAM_SECRET, BODY)));
        }
    }

    @Nested
    class Refuses {

        @Test
        void aMissingHeader() {
            assertThrows(InvalidWebhookSignatureException.class,
                    () -> verifier.verify(WebhookObject.PAGE, BODY, null));
        }

        @Test
        void anotherAlgorithmPrefix() {
            String sha1 = sign(PAGE_SECRET, BODY).replace("sha256=", "sha1=");

            assertThrows(InvalidWebhookSignatureException.class,
                    () -> verifier.verify(WebhookObject.PAGE, BODY, sha1));
        }

        @Test
        void aSignatureThatIsNotHex() {
            assertThrows(InvalidWebhookSignatureException.class,
                    () -> verifier.verify(WebhookObject.PAGE, BODY, "sha256=zz-not-hex"));
        }

        @Test
        void aBodyChangedAfterSigning() {
            String signature = sign(PAGE_SECRET, BODY);

            assertThrows(InvalidWebhookSignatureException.class,
                    () -> verifier.verify(WebhookObject.PAGE, BODY + " ", signature));
        }

        @Test
        void aPageEventSignedWithTheInstagramSecret() {
            assertThrows(InvalidWebhookSignatureException.class,
                    () -> verifier.verify(WebhookObject.PAGE, BODY, sign(INSTAGRAM_SECRET, BODY)));
        }

        @Test
        void withoutTheAppSecretEventsAreNotAcceptedUnchecked() {
            WebhookSignatureVerifier unconfigured = new WebhookSignatureVerifier(
                    new MetaProperties("", "", null, "", "", "unused"), new InstagramProperties("", ""));

            assertThrows(MetaNotConfiguredException.class,
                    () -> unconfigured.verify(WebhookObject.PAGE, BODY, sign(PAGE_SECRET, BODY)));
        }
    }
}
