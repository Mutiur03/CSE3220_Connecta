package com.connecta.backend.meta.service;

import com.connecta.backend.inbox.domain.Channel;
import java.util.UUID;

/** A connected account to read history from: one never imported to the end, or one to sync. */
public record PendingHistoryImport(Channel channel, UUID accountId) {
}
