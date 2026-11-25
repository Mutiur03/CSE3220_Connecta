package com.connecta.backend.meta.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.connecta.backend.config.MetaProperties;
import com.connecta.backend.meta.domain.MetaConnectedAccount;
import com.connecta.backend.meta.repository.MetaConnectedAccountRepository;
import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.support.TestClock;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class InstagramWebhookSubscriberTests {

    private static final String KEY = "dGVzdC1vbmx5LW1ldGEtdG9rZW4ta2V5LTAwMDAwMDE=";

    private final InstagramOAuthClient client = mock(InstagramOAuthClient.class);
    private final MetaConnectedAccountRepository accounts = mock(MetaConnectedAccountRepository.class);
    private final TokenCipher cipher = new TokenCipher(
            new MetaProperties("fb-app", "fb-secret", null, "config", "https://api.example.com", KEY));
    private final InstagramWebhookSubscriber subscriber = new InstagramWebhookSubscriber(client, accounts, cipher);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(InstagramWebhookSubscriber.class);

    @BeforeEach
    void attachAppender() {
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(logs);
    }

    private MetaConnectedAccount account(String externalId, String token) {
        MetaConnectedAccount account = new MetaConnectedAccount(new Organization("Acme", "acme"),
                MetaConnectedAccount.PLATFORM_INSTAGRAM, externalId);
        account.refresh(externalId + "_name", cipher.encrypt(token), null, TestClock.NOW);
        return account;
    }

    private void storedAccounts(MetaConnectedAccount... stored) {
        when(accounts.findAllByPlatform(MetaConnectedAccount.PLATFORM_INSTAGRAM)).thenReturn(List.of(stored));
    }

    @Nested
    class Subscribe {

        @Test
        void subscribesTheAccountWithItsToken() {
            subscriber.subscribe("17841", "ig-token");

            verify(client).subscribeToWebhooks("17841", "ig-token");
        }

        @Test
        void aMetaFailureIsLoggedAtWarnWithTheAccountAndReasonButNeverTheToken() {
            doThrow(new MetaAuthorizationFailedException("Instagram request failed"))
                    .when(client).subscribeToWebhooks("17841", "ig-token");

            assertDoesNotThrow(() -> subscriber.subscribe("17841", "ig-token"));

            ILoggingEvent line = logs.list.get(0);
            assertEquals(Level.WARN, line.getLevel());
            assertEquals("Instagram account 17841 is not subscribed to webhooks: Instagram request failed",
                    line.getFormattedMessage());
            assertFalse(line.getFormattedMessage().contains("ig-token"));
        }
    }

    @Nested
    class CatchUp {

        @Test
        void subscribesOnlyTheAccountsNotAlreadySubscribed() {
            storedAccounts(account("111", "t1"), account("222", "t2"));
            when(client.isSubscribedToWebhooks("111", "t1")).thenReturn(true);
            when(client.isSubscribedToWebhooks("222", "t2")).thenReturn(false);

            subscriber.catchUp();

            verify(client, never()).subscribeToWebhooks("111", "t1");
            verify(client).subscribeToWebhooks("222", "t2");
        }

        @Test
        void aFailedAccountDoesNotStopTheNext() {
            storedAccounts(account("111", "t1"), account("222", "t2"), account("333", "t3"));
            when(client.isSubscribedToWebhooks("111", "t1"))
                    .thenThrow(new MetaAuthorizationFailedException("Instagram request failed"));
            doThrow(new MetaAuthorizationFailedException("Instagram request failed"))
                    .when(client).subscribeToWebhooks("222", "t2");

            subscriber.catchUp();

            verify(client).subscribeToWebhooks("333", "t3");
        }

        @Test
        void noAccountsCallsNothing() {
            storedAccounts();

            subscriber.catchUp();

            verify(client, never()).isSubscribedToWebhooks(anyString(), anyString());
        }

        @Test
        void aDatabaseFailureNeverEscapes() {
            when(accounts.findAllByPlatform(MetaConnectedAccount.PLATFORM_INSTAGRAM))
                    .thenThrow(new IllegalStateException("db"));

            assertDoesNotThrow(subscriber::catchUp);
        }
    }
}
