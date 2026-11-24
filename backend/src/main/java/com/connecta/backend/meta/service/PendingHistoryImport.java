package com.connecta.backend.meta.service;

import com.connecta.backend.inbox.domain.Channel;
import java.util.UUID;

/** A connection whose history has not been imported to the end yet. */
public record PendingHistoryImport(Channel channel, UUID accountId) {
}
