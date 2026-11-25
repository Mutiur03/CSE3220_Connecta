package com.connecta.backend.meta.service;

import com.connecta.backend.config.ProfileLookupConfig;
import com.connecta.backend.meta.domain.MetaConnectedAccount;
import com.connecta.backend.meta.repository.MetaConnectedAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Subscribes Instagram accounts to the app's message webhooks; without it Instagram sends no live events. A
 * connect subscribes its account; at startup (Observer) every stored account that is not yet subscribed is, in
 * the background, so accounts connected before this existed get fixed without a reconnect. A failure never
 * reaches the caller: it is logged at WARN with the account id and the reason, never the token.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InstagramWebhookSubscriber {

    private final InstagramOAuthClient client;
    private final MetaConnectedAccountRepository accounts;
    private final TokenCipher cipher;

    /** Subscribes the account; a Meta failure is logged and swallowed so the connect still succeeds. */
    public void subscribe(String accountId, String accessToken) {
        try {
            client.subscribeToWebhooks(accountId, accessToken);
        } catch (MetaAuthorizationFailedException e) {
            log.warn("Instagram account {} is not subscribed to webhooks: {}", accountId, e.getMessage());
        }
    }

    /** Startup catch-up: idempotent, only accounts Meta does not already report as subscribed are subscribed. */
    @Async(ProfileLookupConfig.EXECUTOR)
    @EventListener(ApplicationReadyEvent.class)
    public void catchUp() {
        try {
            accounts.findAllByPlatform(MetaConnectedAccount.PLATFORM_INSTAGRAM).forEach(this::subscribeIfMissing);
        } catch (RuntimeException e) {
            log.warn("Instagram webhook catch-up could not run: {}", e.getClass().getSimpleName());
        }
    }

    private void subscribeIfMissing(MetaConnectedAccount account) {
        String accountId = account.getExternalAccountId();
        try {
            String token = cipher.decrypt(account.getAccessTokenEncrypted());
            if (!client.isSubscribedToWebhooks(accountId, token)) {
                subscribe(accountId, token);
            }
        } catch (MetaAuthorizationFailedException | TokenDecryptionException e) {
            log.warn("Instagram account {} webhook subscription could not be checked: {}", accountId,
                    e.getClass().getSimpleName());
        }
    }
}
