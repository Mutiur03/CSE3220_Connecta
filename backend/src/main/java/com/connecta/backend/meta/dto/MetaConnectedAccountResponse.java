package com.connecta.backend.meta.dto;

import com.connecta.backend.meta.domain.MetaConnectedAccount;
import java.time.Instant;
import java.util.UUID;

/** A connected Meta account. The access token is never returned. */
public record MetaConnectedAccountResponse(UUID id, String externalAccountId, String username, Instant connectedAt) {

    public static MetaConnectedAccountResponse from(MetaConnectedAccount account) {
        return new MetaConnectedAccountResponse(account.getId(), account.getExternalAccountId(),
                account.getUsername(), account.getUpdatedAt());
    }
}
