package com.connecta.backend.meta.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.connecta.backend.config.InstagramProperties;
import com.connecta.backend.config.MetaProperties;
import com.connecta.backend.config.MetaWebhookProperties;
import com.connecta.backend.meta.domain.MetaWebhookEvent;
import com.connecta.backend.meta.domain.WebhookObject;
import com.connecta.backend.meta.repository.MetaWebhookEventRepository;
import com.connecta.backend.support.TestClock;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

class WebhookIntakeServiceTests {

    private static final String PAGE_SECRET = "page-app-secret";
    private static final String INSTAGRAM_SECRET = "instagram-app-secret";
    private static final String VERIFY_TOKEN = "expected-verify-token";
    private static final String PAGE_BODY = "{\"object\":\"page\",\"entry\":[{\"id\":\"1\"}]}";
    private static final String INSTAGRAM_BODY = "{\"object\":\"instagram\",\"entry\":[{\"id\":\"2\"}]}";

    private MetaWebhookEventRepository events;
    private WebhookEventProcessor processor;
    private WebhookIntakeService service;

    @BeforeEach
    void setUp() {
        events = mock(MetaWebhookEventRepository.class);
        processor = mock(WebhookEventProcessor.class);
        when(events.save(any(MetaWebhookEvent.class))).thenAnswer(call -> call.getArgument(0));
        WebhookSignatureVerifier verifier = new WebhookSignatureVerifier(
                new MetaProperties("app", PAGE_SECRET, null, "config", "https://api.example.com", "unused"),
                new InstagramProperties("ig-app", INSTAGRAM_SECRET));
        service = new WebhookIntakeService(verifier, new MetaWebhookProperties(VERIFY_TOKEN), events, processor,
                new ObjectMapper(), TestClock.FIXED);
    }

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
    class Receive {

        @Test
        void storesAVerifiedPageEventAsReceived() {
            service.receive(PAGE_BODY, sign(PAGE_SECRET, PAGE_BODY));

            ArgumentCaptor<MetaWebhookEvent> captor = ArgumentCaptor.forClass(MetaWebhookEvent.class);
            verify(events).save(captor.capture());
            assertEquals(WebhookObject.PAGE, captor.getValue().getObject());
            assertEquals(PAGE_BODY, captor.getValue().getPayload());
            assertEquals(TestClock.NOW, captor.getValue().getReceivedAt());
            verify(processor).process(captor.getValue());
        }

        @Test
        void storesAVerifiedInstagramEvent() {
            service.receive(INSTAGRAM_BODY, sign(INSTAGRAM_SECRET, INSTAGRAM_BODY));

            ArgumentCaptor<MetaWebhookEvent> captor = ArgumentCaptor.forClass(MetaWebhookEvent.class);
            verify(events).save(captor.capture());
            assertEquals(WebhookObject.INSTAGRAM, captor.getValue().getObject());
        }

        @Test
        void aBadSignatureStoresNothing() {
            assertThrows(InvalidWebhookSignatureException.class,
                    () -> service.receive(PAGE_BODY, sign(INSTAGRAM_SECRET, PAGE_BODY)));

            verify(events, never()).save(any());
            verify(processor, never()).process(any());
        }

        @Test
        void aMissingSignatureStoresNothing() {
            assertThrows(InvalidWebhookSignatureException.class, () -> service.receive(PAGE_BODY, null));

            verify(events, never()).save(any());
        }

        @Test
        void aBodyThatIsNotJsonIsRejectedAndStoresNothing() {
            assertThrows(MalformedWebhookException.class,
                    () -> service.receive("not json", sign(PAGE_SECRET, "not json")));

            verify(events, never()).save(any());
        }

        @Test
        void aJsonArrayIsNotAWebhookAndIsRejected() {
            assertThrows(MalformedWebhookException.class, () -> service.receive("[1,2]", null));
        }

        @Test
        void otherProductsAreAcknowledgedWithoutBeingStored() {
            service.receive("{\"object\":\"whatsapp_business_account\"}", null);

            verify(events, never()).save(any());
            verify(processor, never()).process(any());
        }
    }

    @Nested
    class ConfirmSubscription {

        @Test
        void returnsTheChallengeWhenTheTokenMatches() {
            assertEquals("12345", service.confirmSubscription("subscribe", VERIFY_TOKEN, "12345"));
        }

        @Test
        void aWrongTokenIsRefused() {
            assertThrows(WebhookVerificationFailedException.class,
                    () -> service.confirmSubscription("subscribe", "wrong", "12345"));
        }

        @Test
        void aWrongModeIsRefused() {
            assertThrows(WebhookVerificationFailedException.class,
                    () -> service.confirmSubscription("unsubscribe", VERIFY_TOKEN, "12345"));
        }

        @Test
        void noConfiguredTokenRefusesEverySubscription() {
            WebhookIntakeService unconfigured = new WebhookIntakeService(
                    new WebhookSignatureVerifier(
                            new MetaProperties("", "", null, "", "", "unused"), new InstagramProperties("", "")),
                    new MetaWebhookProperties(""), events, processor, new ObjectMapper(), TestClock.FIXED);

            assertThrows(WebhookVerificationFailedException.class,
                    () -> unconfigured.confirmSubscription("subscribe", "", "12345"));
        }
    }
}
