package com.connecta.backend.meta.dto;

import com.connecta.backend.meta.domain.HistoryImportFailure;
import com.connecta.backend.meta.domain.MetaConnectedAccount;
import java.time.Instant;
import java.util.UUID;

/**
 * A connected Meta account. The access token is never returned. {@code historyImportError} is set while the
 * last import of past conversations failed ({@code PERMISSION} or {@code FETCH}), else null.
 * {@code lastWebhookAt} is when a webhook last arrived for the account, null until one does.
 */
public record MetaConnectedAccountResponse(UUID id, String externalAccountId, String username, Instant connectedAt,
        HistoryImportFailure historyImportError, Instant historyImportFailedAt, Instant lastWebhookAt) {

    public static MetaConnectedAccountResponse from(MetaConnectedAccount account) {
        return new MetaConnectedAccountResponse(account.getId(), account.getExternalAccountId(),
                account.getUsername(), account.getUpdatedAt(), account.getHistoryImportError(),
                account.getHistoryImportFailedAt(), account.getLastWebhookAt());
    }
}
