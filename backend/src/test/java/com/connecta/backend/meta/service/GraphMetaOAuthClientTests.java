package com.connecta.backend.meta.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.connecta.backend.config.MetaProperties;
import com.connecta.backend.meta.service.MetaOAuthClient.MetaUserToken;
import com.connecta.backend.support.TestClock;
import java.time.Clock;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GraphMetaOAuthClientTests {

    private static final String KEY = "dGVzdC1vbmx5LW1ldGEtdG9rZW4ta2V5LTAwMDAwMDE=";
    private static final Clock CLOCK = TestClock.FIXED;
    private static final String TOKEN_URL = "https://graph.facebook.com/v26.0/oauth/access_token";

    private MockRestServiceServer graph;
    private GraphMetaOAuthClient client;

    @BeforeEach
    void setUp() {
        MetaProperties props = new MetaProperties("test-app-id", "test-app-secret", "v26.0", "test-config-id",
                "https://api.example.com", KEY);
        RestClient.Builder builder = RestClient.builder();
        graph = MockRestServiceServer.bindTo(builder).build();
        client = new GraphMetaOAuthClient(props, builder, CLOCK);
    }

    @Nested
    class AuthorizationUrl {

        @Test
        void pointsAtTheLoginDialogWithTheAppConfigAndState() {
            String url = client.authorizationUrl("signed-state", "https://api.example.com/api/meta/oauth/callback");

            assertTrue(url.startsWith("https://www.facebook.com/v26.0/dialog/oauth?"));
            assertTrue(url.contains("client_id=test-app-id"));
            assertTrue(url.contains("config_id=test-config-id"));
            assertTrue(url.contains("response_type=code"));
            assertTrue(url.contains("state=signed-state"));
        }
    }

    @Nested
    class TokenExchange {

        @Test
        void exchangeCodeReadsTheShortLivedToken() {
            graph.expect(requestTo(startsWith(TOKEN_URL)))
                    .andExpect(queryParam("code", "the-code"))
                    .andRespond(withSuccess("{\"access_token\":\"short\",\"expires_in\":3600}",
                            MediaType.APPLICATION_JSON));

            MetaUserToken token = client.exchangeCode("the-code", "https://api.example.com/cb");

            assertEquals("short", token.accessToken());
            assertEquals(CLOCK.instant().plusSeconds(3600), token.expiresAt());
            graph.verify();
        }

        @Test
        void extendTokenAsksForALongLivedTokenThroughFbExchange() {
            graph.expect(requestTo(startsWith(TOKEN_URL)))
                    .andExpect(queryParam("grant_type", "fb_exchange_token"))
                    .andExpect(queryParam("fb_exchange_token", "short"))
                    .andRespond(withSuccess("{\"access_token\":\"long\",\"expires_in\":5184000}",
                            MediaType.APPLICATION_JSON));

            MetaUserToken token = client.extendToken("short");

            assertEquals("long", token.accessToken());
            assertEquals(CLOCK.instant().plusSeconds(5_184_000), token.expiresAt());
            graph.verify();
        }

        @Test
        void aGraphErrorBecomesAGenericFailureWithoutMetasText() {
            graph.expect(requestTo(startsWith(TOKEN_URL)))
                    .andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON)
                            .body("{\"error\":{\"message\":\"Invalid verification code\",\"code\":100}}"));

            MetaAuthorizationFailedException e = assertThrows(MetaAuthorizationFailedException.class,
                    () -> client.exchangeCode("bad", "https://api.example.com/cb"));

            assertFalse(e.getMessage().contains("Invalid verification code"));
        }
    }

    private static org.hamcrest.Matcher<String> startsWith(String prefix) {
        return org.hamcrest.Matchers.startsWith(prefix);
    }
}
