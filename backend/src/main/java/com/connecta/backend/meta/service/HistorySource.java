package com.connecta.backend.meta.service;

import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.meta.domain.ConnectedChannelAccount;
import java.util.UUID;

/** A connected account ready to be read: who it is, its Meta id and its encrypted token. */
record HistorySource(ConnectedChannelAccount account, UUID accountId, Channel channel, UUID organizationId,
        String externalId, String encryptedToken) {

    /** Without the token, so a log line can never carry it. */
    @Override
    public String toString() {
        return "HistorySource[" + channel + " " + accountId + "]";
    }
}
