package com.connecta.backend.meta.service;

import com.connecta.backend.inbox.domain.Channel;
import java.util.Iterator;

/** Adapter boundary to the provider's conversations API: reads past conversations, never writes. */
public interface ConversationHistoryClient {

    Channel channel();

    /**
     * The account's conversations, most recently updated first, read one Meta page at a time as the iterator is
     * consumed. A Meta failure surfaces from {@code hasNext} or {@code next} as a {@link HistoryFetchException}
     * or {@link HistoryPermissionException}.
     */
    Iterator<HistoryConversation> conversations(String accountExternalId, String accessToken);

    /** The same read with its own bounds, so the recent-message sync can ask for less than a full import. */
    Iterator<HistoryConversation> conversations(String accountExternalId, String accessToken, HistoryBounds bounds);
}
