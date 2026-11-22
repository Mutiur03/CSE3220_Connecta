package com.connecta.backend.meta.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.connecta.backend.meta.repository.MetaWebhookEventRepository;
import com.connecta.backend.support.RandomJwtSecret;
import com.connecta.backend.support.TestClock;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Meta's webhook endpoints against real PostgreSQL. The subscription check (GET) and the signed delivery (POST) are
 * both public, so the tests cover the checks themselves: wrong token, missing or bad signature, malformed body,
 * and that only verified events are stored. Skipped without Docker.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false"})
@AutoConfigureMockMvc
@Import(TestClock.class)
@ContextConfiguration(initializers = RandomJwtSecret.class)
class MetaWebhookControllerIntegrationTests {

    private static final String PATH = "/api/meta/webhooks";
    private static final String SIGNATURE = "X-Hub-Signature-256";
    private static final String PAGE_BODY = "{\"object\":\"page\",\"entry\":[{\"id\":\"42\"}]}";
    private static final String INSTAGRAM_BODY = "{\"object\":\"instagram\",\"entry\":[{\"id\":\"43\"}]}";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    MetaWebhookEventRepository events;

    private static String sign(String secret, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static MockHttpServletRequestBuilder deliver(String body, String signature) {
        MockHttpServletRequestBuilder request = post(PATH).contentType(MediaType.APPLICATION_JSON).content(body);
        return signature == null ? request : request.header(SIGNATURE, signature);
    }

    @Nested
    class Subscribe {

        @Test
        void returnsTheChallengeWhenTheVerifyTokenMatches() throws Exception {
            mvc.perform(get(PATH).param("hub.mode", "subscribe")
                            .param("hub.verify_token", "test-webhook-verify-token")
                            .param("hub.challenge", "1158201444"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("1158201444"));
        }

        @Test
        void aWrongVerifyTokenIsForbidden() throws Exception {
            mvc.perform(get(PATH).param("hub.mode", "subscribe")
                            .param("hub.verify_token", "not-the-token")
                            .param("hub.challenge", "1"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.detail").value("Webhook verification failed"));
        }

        @Test
        void aMissingVerifyTokenParameterIsBadRequest() throws Exception {
            mvc.perform(get(PATH).param("hub.mode", "subscribe").param("hub.challenge", "1"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    class Deliver {

        @Test
        void aSignedPageEventIsStoredAsReceived() throws Exception {
            long before = events.count();

            mvc.perform(deliver(PAGE_BODY, sign("test-app-secret", PAGE_BODY)))
                    .andExpect(status().isOk());

            assertEquals(before + 1, events.count());
            assertEquals(PAGE_BODY, events.findAll().stream()
                    .filter(e -> PAGE_BODY.equals(e.getPayload())).findFirst().orElseThrow().getPayload());
        }

        @Test
        void aSignedInstagramEventIsStoredUnderTheInstagramSecret() throws Exception {
            long before = events.count();

            mvc.perform(deliver(INSTAGRAM_BODY, sign("test-instagram-app-secret", INSTAGRAM_BODY)))
                    .andExpect(status().isOk());

            assertEquals(before + 1, events.count());
        }

        @Test
        void aBadSignatureIsUnauthorizedAndStoresNothing() throws Exception {
            long before = events.count();

            mvc.perform(deliver(PAGE_BODY, sign("wrong-secret", PAGE_BODY)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.detail").value("Invalid webhook signature"));

            assertEquals(before, events.count());
        }

        @Test
        void aMissingSignatureIsUnauthorized() throws Exception {
            mvc.perform(deliver(PAGE_BODY, null)).andExpect(status().isUnauthorized());
        }

        @Test
        void aSignedBodyThatIsNotJsonIsBadRequest() throws Exception {
            String body = "not json";

            mvc.perform(deliver(body, sign("test-app-secret", body)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("Webhook body is not valid JSON"));
        }

        @Test
        void anotherProductIsAcknowledgedAndNotStored() throws Exception {
            long before = events.count();
            String body = "{\"object\":\"whatsapp_business_account\"}";

            mvc.perform(deliver(body, null)).andExpect(status().isOk());

            assertEquals(before, events.count());
        }
    }
}
