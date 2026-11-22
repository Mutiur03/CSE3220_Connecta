package com.connecta.backend.meta.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.connecta.backend.config.InstagramProperties;
import com.connecta.backend.config.MetaProperties;
import com.connecta.backend.meta.service.InstagramOAuthClient.InstagramAccount;
import com.connecta.backend.meta.service.MetaOAuthClient.MetaUserToken;
import com.connecta.backend.support.TestClock;
import java.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GraphInstagramOAuthClientTests {

    private static final String KEY = "dGVzdC1vbmx5LW1ldGEtdG9rZW4ta2V5LTAwMDAwMDE=";
    private static final Clock CLOCK = TestClock.FIXED;
    private static final String SHORT_URL = "https://api.instagram.com/oauth/access_token";
    private static final String LONG_URL = "https://graph.instagram.com/v26.0/access_token";
    private static final String ME_URL = "https://graph.instagram.com/v26.0/me";

    private MockRestServiceServer server;
    private GraphInstagramOAuthClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        MetaProperties meta = new MetaProperties("fb-app", "fb-secret", "v26.0", "config",
                "https://api.example.com", KEY);
        InstagramProperties instagram = new InstagramProperties("ig-app", "ig-secret");
        client = new GraphInstagramOAuthClient(instagram, meta, builder, CLOCK);
    }

    @Nested
    class AuthorizationUrl {

        @Test
        void pointsAtInstagramLoginWithTheInstagramAppAndBusinessScopes() {
            String url = client.authorizationUrl("signed-state", "https://api.example.com/api/meta/instagram/callback");

            assertTrue(url.startsWith("https://www.instagram.com/oauth/authorize?"));
            assertTrue(url.contains("client_id=ig-app"));
            assertTrue(url.contains("instagram_business_manage_messages"));
            assertTrue(url.contains("state=signed-state"));
        }
    }

    @Nested
    class ShortLivedToken {

        @Test
        void readsAFlatResponse() {
            server.expect(requestTo(SHORT_URL)).andExpect(method(HttpMethod.POST))
                    .andRespond(withSuccess("{\"access_token\":\"short\",\"user_id\":17841}", MediaType.APPLICATION_JSON));

            assertEquals("short", client.exchangeCode("code", "https://api.example.com/cb").accessToken());
        }

        @Test
        void readsTheWrappedDataResponse() {
            server.expect(requestTo(SHORT_URL)).andExpect(method(HttpMethod.POST))
                    .andRespond(withSuccess("{\"data\":[{\"access_token\":\"wrapped\",\"user_id\":17841}]}",
                            MediaType.APPLICATION_JSON));

            assertEquals("wrapped", client.exchangeCode("code", "https://api.example.com/cb").accessToken());
        }

        @Test
        void anEmptyAnswerIsAGenericFailure() {
            server.expect(requestTo(SHORT_URL)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

            assertThrows(MetaAuthorizationFailedException.class,
                    () -> client.exchangeCode("code", "https://api.example.com/cb"));
        }
    }

    @Nested
    class LongLivedTokenAndAccount {

        @Test
        void extendsTheTokenThroughIgExchange() {
            server.expect(requestTo(startsWithUrl(LONG_URL)))
                    .andExpect(queryParam("grant_type", "ig_exchange_token"))
                    .andRespond(withSuccess("{\"access_token\":\"long\",\"expires_in\":5184000}",
                            MediaType.APPLICATION_JSON));

            MetaUserToken token = client.extendToken("short");

            assertEquals("long", token.accessToken());
            assertEquals(CLOCK.instant().plusSeconds(5_184_000), token.expiresAt());
        }

        @Test
        void readsTheProfessionalAccountFromMe() {
            server.expect(requestTo(startsWithUrl(ME_URL)))
                    .andRespond(withSuccess("{\"user_id\":\"17841\",\"username\":\"acme_shop\"}",
                            MediaType.APPLICATION_JSON));

            InstagramAccount account = client.fetchAccount("long");

            assertEquals("17841", account.userId());
            assertEquals("acme_shop", account.username());
        }

        @Test
        void aGraphErrorBecomesAGenericFailureWithoutTheGraphText() {
            server.expect(requestTo(startsWithUrl(ME_URL)))
                    .andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON)
                            .body("{\"error\":{\"message\":\"Invalid OAuth access token\",\"code\":190}}"));

            MetaAuthorizationFailedException e = assertThrows(MetaAuthorizationFailedException.class,
                    () -> client.fetchAccount("bad"));

            assertFalse(e.getMessage().contains("Invalid OAuth access token"));
            assertEquals("Instagram request failed", e.getMessage());
        }
    }

    private static org.hamcrest.Matcher<String> startsWithUrl(String prefix) {
        return org.hamcrest.Matchers.startsWith(prefix);
    }
}
