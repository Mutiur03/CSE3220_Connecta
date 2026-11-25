package com.connecta.backend.meta.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.connecta.backend.config.MetaProperties;
import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.inbox.service.ProfileTarget;
import com.connecta.backend.meta.domain.MetaConnectedAccount;
import com.connecta.backend.meta.repository.MetaConnectedAccountRepository;
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

class InstagramCustomerProfileClientTests {

    private static final String KEY = "dGVzdC1vbmx5LW1ldGEtdG9rZW4ta2V5LTAwMDAwMDE=";
    private static final String URL = "https://graph.instagram.com/v27.0/igsid-1?fields=name,username,profile_pic";

    private final UUID organizationId = UUID.randomUUID();
    private final UUID accountRowId = UUID.randomUUID();
    private MetaConnectedAccountRepository accounts;
    private MockRestServiceServer server;
    private InstagramCustomerProfileClient client;
    private MetaConnectedAccount account;

    @BeforeEach
    void setUp() {
        accounts = mock(MetaConnectedAccountRepository.class);
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        MetaProperties properties = new MetaProperties("app", "secret", "v27.0", "config", "https://x", KEY);
        TokenCipher cipher = new TokenCipher(properties);
        account = new MetaConnectedAccount(new Organization("Acme", "acme"), MetaConnectedAccount.PLATFORM_INSTAGRAM,
                "ig-1");
        account.refresh("shop", cipher.encrypt("ig-token"), null, Instant.EPOCH);
        client = new InstagramCustomerProfileClient(accounts, cipher, properties, builder);
    }

    private ProfileTarget customer(UUID linkedAccount) {
        return new ProfileTarget(organizationId, UUID.randomUUID(), Channel.INSTAGRAM, "igsid-1", null,
                linkedAccount);
    }

    private void respond(String body) {
        server.expect(requestTo(URL)).andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer ig-token"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    @Test
    void servesTheInstagramChannel() {
        assertEquals(Channel.INSTAGRAM, client.channel());
    }

    @Nested
    class Fetching {

        @BeforeEach
        void linkedAccount() {
            when(accounts.findByIdAndOrganizationId(accountRowId, organizationId)).thenReturn(Optional.of(account));
        }

        @Test
        void asksForNameAndUsernameWithABearerTokenAndPrefersTheName() {
            respond("{\"name\":\"Rina Das\",\"username\":\"rina\",\"id\":\"igsid-1\"}");

            assertEquals(Optional.of(new CustomerProfile("Rina Das", null)), client.fetchProfile(customer(accountRowId)));
            server.verify();
        }

        @Test
        void fallsBackToTheUsernameWhenThereIsNoName() {
            respond("{\"username\":\"rina\"}");

            assertEquals(Optional.of(new CustomerProfile("rina", null)), client.fetchProfile(customer(accountRowId)));
        }

        @Test
        void aBlankNameFallsBackToTheUsername() {
            respond("{\"name\":\"  \",\"username\":\"rina\"}");

            assertEquals(Optional.of(new CustomerProfile("rina", null)), client.fetchProfile(customer(accountRowId)));
        }

        @Test
        void neitherNameNorUsernameIsEmpty() {
            respond("{\"id\":\"igsid-1\"}");

            assertEquals(Optional.empty(), client.fetchProfile(customer(accountRowId)));
        }

        @Test
        void aGraphErrorIsEmpty() {
            server.expect(requestTo(URL)).andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\":{\"code\":230}}"));

            assertEquals(Optional.empty(), client.fetchProfile(customer(accountRowId)));
        }

        @Test
        void readsTheHttpsProfilePicture() {
            respond("{\"name\":\"Rina\",\"profile_pic\":\"https://cdn.example/p.jpg?sig=1\"}");

            assertEquals(Optional.of(new CustomerProfile("Rina", "https://cdn.example/p.jpg?sig=1")),
                    client.fetchProfile(customer(accountRowId)));
        }

        @Test
        void aMissingPictureLeavesTheNameOnly() {
            respond("{\"name\":\"Rina\"}");

            assertEquals(Optional.of(new CustomerProfile("Rina", null)), client.fetchProfile(customer(accountRowId)));
        }

        @Test
        void aPictureWithoutANameIsStillAProfile() {
            respond("{\"profile_pic\":\"https://cdn.example/p.jpg\"}");

            assertEquals(Optional.of(new CustomerProfile(null, "https://cdn.example/p.jpg")),
                    client.fetchProfile(customer(accountRowId)));
        }

        @Test
        void aNonHttpsPictureIsRejected() {
            respond("{\"name\":\"Rina\",\"profile_pic\":\"http://cdn.example/p.jpg\"}");
            respond("{\"name\":\"Rina\",\"profile_pic\":\"javascript:alert(1)\"}");

            assertEquals(Optional.of(new CustomerProfile("Rina", null)), client.fetchProfile(customer(accountRowId)));
            assertEquals(Optional.of(new CustomerProfile("Rina", null)), client.fetchProfile(customer(accountRowId)));
        }

        @Test
        void anOverlongPictureUrlIsRejected() {
            respond("{\"name\":\"Rina\",\"profile_pic\":\"https://cdn.example/" + "a".repeat(3000) + "\"}");

            assertEquals(Optional.of(new CustomerProfile("Rina", null)), client.fetchProfile(customer(accountRowId)));
        }
    }

    @Nested
    class FindingTheAccount {

        @Test
        void aLinkedAccountOfAnotherOrganizationIsNotConnected() {
            when(accounts.findByIdAndOrganizationId(accountRowId, organizationId)).thenReturn(Optional.empty());

            assertThrows(ChannelNotConnectedException.class, () -> client.fetchProfile(customer(accountRowId)));
        }

        @Test
        void withoutALinkTheOnlyAccountOfTheOrganizationIsUsed() {
            when(accounts.findByOrganizationIdAndPlatform(any(UUID.class), eq("INSTAGRAM"), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(account)));
            respond("{\"name\":\"Rina\"}");

            assertEquals(Optional.of(new CustomerProfile("Rina", null)), client.fetchProfile(customer(null)));
        }

        @Test
        void withoutALinkSeveralAccountsAreAmbiguous() {
            when(accounts.findByOrganizationIdAndPlatform(any(UUID.class), eq("INSTAGRAM"), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(account, account)));

            assertThrows(ConversationNotLinkedException.class, () -> client.fetchProfile(customer(null)));
        }
    }
}
