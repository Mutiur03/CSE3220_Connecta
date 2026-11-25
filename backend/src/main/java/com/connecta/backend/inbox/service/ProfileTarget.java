package com.connecta.backend.inbox.service;

import com.connecta.backend.inbox.domain.Channel;
import java.util.UUID;

/**
 * A customer whose profile (name, picture) is due for a lookup, with what the lookup needs: the channel, the
 * provider id and the connected account the conversation belongs to (both null on older rows). Plain ids, so
 * it is safe to use after the loading transaction has ended.
 */
public record ProfileTarget(UUID organizationId, UUID conversationId, Channel channel, String externalId,
        UUID metaPageId, UUID instagramAccountId) {
}
