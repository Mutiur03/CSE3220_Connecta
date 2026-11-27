package com.connecta.backend.meta.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.connecta.backend.config.MetaProperties;
import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.inbox.service.ProfileTarget;
import com.connecta.backend.meta.domain.MetaConnectedPage;
import com.connecta.backend.meta.repository.MetaConnectedPageRepository;
import com.connecta.backend.org.domain.Organization;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class MessengerCustomerProfileClientTests {

    private static final String KEY = "dGVzdC1vbmx5LW1ldGEtdG9rZW4ta2V5LTAwMDAwMDE=";
    private static final String URL = "https://graph.facebook.com/v27.0/psid-1?fields=name,profile_pic";

    private final UUID organizationId = UUID.randomUUID();
    private final UUID pageRowId = UUID.randomUUID();
    private MetaConnectedPageRepository pages;
    private MockRestServiceServer server;
    private MessengerCustomerProfileClient client;
    private MetaConnectedPage page;

    @BeforeEach
    void setUp() {
        pages = mock(MetaConnectedPageRepository.class);
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        MetaProperties properties = new MetaProperties("app", "secret", "v27.0", "config", "https://x", KEY);
        TokenCipher cipher = new TokenCipher(properties);
        page = new MetaConnectedPage(new Organization("Acme", "acme"), "page-1");
        page.replace("Page", cipher.encrypt("page-token"), null, Instant.EPOCH);
        client = new MessengerCustomerProfileClient(pages, cipher, properties, builder);
    }

    private ProfileTarget customer(UUID linkedPage) {
        return new ProfileTarget(organizationId, UUID.randomUUID(), Channel.MESSENGER, "psid-1", linkedPage, null);
    }

    private void respondOk(String body) {
        server.expect(requestTo(URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    @Test
    void servesTheMessengerChannel() {
        assertEquals(Channel.MESSENGER, client.channel());
    }

    @Nested
    class Fetching {

        @BeforeEach
        void linkedPage() {
            when(pages.findByIdAndOrganizationId(pageRowId, organizationId)).thenReturn(Optional.of(page));
        }

        @Test
        void theProfileRequestCarriesNoAccessTokenInTheUrl() {
            server.expect(request -> assertFalse(request.getURI().getRawQuery().contains("access_token")))
                    .andExpect(header("Authorization", "Bearer page-token"))
                    .andRespond(withSuccess("{\"name\":\"Rina\"}", MediaType.APPLICATION_JSON));

            client.fetchProfile(customer(pageRowId));

            server.verify();
        }

        @Test
        void asksForTheNameWithThePageToken() {
            server.expect(requestTo(URL)).andExpect(method(HttpMethod.GET))
                    .andExpect(header("Authorization", "Bearer page-token")).andRespond(withSuccess(
                    "{\"name\":\"Rina Das\",\"id\":\"psid-1\"}", MediaType.APPLICATION_JSON));

            assertEquals(Optional.of(new CustomerProfile("Rina Das", null)), client.fetchProfile(customer(pageRowId)));
            server.verify();
        }

        @Test
        void aMissingOrBlankNameIsEmpty() {
            server.expect(requestTo(URL)).andRespond(withSuccess("{\"id\":\"psid-1\"}", MediaType.APPLICATION_JSON));
            server.expect(requestTo(URL)).andRespond(withSuccess("{\"name\":\" \"}", MediaType.APPLICATION_JSON));

            assertEquals(Optional.empty(), client.fetchProfile(customer(pageRowId)));
            assertEquals(Optional.empty(), client.fetchProfile(customer(pageRowId)));
        }

        @Test
        void aGraphErrorSuchAsMissingConsentIsEmpty() {
            server.expect(requestTo(URL)).andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\":{\"code\":100,\"message\":\"secret detail\"}}"));

            assertEquals(Optional.empty(), client.fetchProfile(customer(pageRowId)));
        }

        @Test
        void aGraphErrorWithoutAJsonBodyStillGivesAnEmptyProfile() {
            server.expect(requestTo(URL)).andRespond(withBadRequest().contentType(MediaType.TEXT_PLAIN).body("nope"));

            assertEquals(Optional.empty(), client.fetchProfile(customer(pageRowId)));
        }

        @Test
        void aServerErrorIsEmpty() {
            server.expect(requestTo(URL)).andRespond(withServerError());

            assertEquals(Optional.empty(), client.fetchProfile(customer(pageRowId)));
        }

        @Test
        void readsTheHttpsProfilePicture() {
            respondOk("{\"name\":\"Rina\",\"profile_pic\":\"https://cdn.example/p.jpg?sig=1\"}");

            assertEquals(Optional.of(new CustomerProfile("Rina", "https://cdn.example/p.jpg?sig=1")),
                    client.fetchProfile(customer(pageRowId)));
        }

        @Test
        void aMissingPictureLeavesTheNameOnly() {
            respondOk("{\"name\":\"Rina\"}");

            assertEquals(Optional.of(new CustomerProfile("Rina", null)), client.fetchProfile(customer(pageRowId)));
        }

        @Test
        void aPictureWithoutANameIsStillAProfile() {
            respondOk("{\"profile_pic\":\"https://cdn.example/p.jpg\"}");

            assertEquals(Optional.of(new CustomerProfile(null, "https://cdn.example/p.jpg")),
                    client.fetchProfile(customer(pageRowId)));
        }

        @Test
        void aNonHttpsPictureIsRejected() {
            respondOk("{\"name\":\"Rina\",\"profile_pic\":\"http://cdn.example/p.jpg\"}");
            respondOk("{\"name\":\"Rina\",\"profile_pic\":\"javascript:alert(1)\"}");

            assertEquals(Optional.of(new CustomerProfile("Rina", null)), client.fetchProfile(customer(pageRowId)));
            assertEquals(Optional.of(new CustomerProfile("Rina", null)), client.fetchProfile(customer(pageRowId)));
        }

        @Test
        void anOverlongPictureUrlIsRejected() {
            respondOk("{\"name\":\"Rina\",\"profile_pic\":\"https://cdn.example/" + "a".repeat(3000) + "\"}");

            assertEquals(Optional.of(new CustomerProfile("Rina", null)), client.fetchProfile(customer(pageRowId)));
        }
    }

    @Nested
    class FindingThePage {

        @Test
        void aLinkedPageOfAnotherOrganizationIsNotConnected() {
            when(pages.findByIdAndOrganizationId(pageRowId, organizationId)).thenReturn(Optional.empty());

            assertThrows(ChannelNotConnectedException.class, () -> client.fetchProfile(customer(pageRowId)));
        }

        @Test
        void withoutALinkTheOnlyPageOfTheOrganizationIsUsed() {
            when(pages.findByOrganizationId(any(UUID.class), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(page)));
            server.expect(requestTo(URL)).andRespond(withSuccess("{\"name\":\"Rina\"}", MediaType.APPLICATION_JSON));

            assertEquals(Optional.of(new CustomerProfile("Rina", null)), client.fetchProfile(customer(null)));
        }

        @Test
        void withoutALinkSeveralPagesAreAmbiguous() {
            when(pages.findByOrganizationId(any(UUID.class), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(page, page)));

            assertThrows(ConversationNotLinkedException.class, () -> client.fetchProfile(customer(null)));
        }

        @Test
        void withoutALinkAndNoPageItIsNotConnected() {
            when(pages.findByOrganizationId(any(UUID.class), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            assertThrows(ChannelNotConnectedException.class, () -> client.fetchProfile(customer(null)));
        }
    }
}
