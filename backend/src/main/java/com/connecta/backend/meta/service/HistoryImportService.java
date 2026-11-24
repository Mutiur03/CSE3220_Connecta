package com.connecta.backend.meta.service;

import com.connecta.backend.config.HistoryImportProperties;
import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.inbox.service.HistoryImportedEvent;
import com.connecta.backend.inbox.service.IncomingMessageService;
import com.connecta.backend.meta.domain.HistoryImportFailure;
import com.connecta.backend.inbox.service.ParticipantNameService;
import java.time.Clock;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Imports one connected account past conversations through the normal inbox pipeline (idempotent, tenant
 * scoped). Meta is read page by page and every message is stored in its own short transaction, so no connection
 * is held across a Meta call and a failure part-way keeps what was stored; running again continues. Conversations
 * are read newest first, each stored oldest message first so the conversation last activity ends up right.
 * Stops at the configured limits and then reports {@code truncated}. After a successful run the account is marked
 * imported and one {@link HistoryImportedEvent} is published (no per-message pushes).
 *
 * <p>Only one import per account runs at a time: the guard is in memory, so it holds for a single server
 * instance. A second instance would only repeat work, since storing is idempotent.
 */
@Service
@RequiredArgsConstructor
public class HistoryImportService {

    private final HistoryAccountStore store;
    private final ConversationHistoryClientFactory clients;
    private final TokenCipher cipher;
    private final IncomingMessageService inbox;
    private final ParticipantNameService names;
    private final ApplicationEventPublisher publisher;
    private final HistoryImportProperties limits;
    private final Clock clock;
    private final Set<UUID> running = ConcurrentHashMap.newKeySet();

    /**
     * Imports the account history. Empty when the account no longer exists.
     *
     * @throws HistoryImportInProgressException when this account is already being imported
     * @throws HistoryFetchException when Meta fails; what was stored stays, the account is not marked
     *         imported and the failure code is recorded
     * @throws HistoryPermissionException when Meta refuses to share conversations
     */
    public Optional<HistoryImportResult> importHistory(Channel channel, UUID accountId) {
        Optional<HistorySource> source = store.find(channel, accountId);
        if (source.isEmpty()) {
            return Optional.empty();
        }
        if (!running.add(accountId)) {
            throw new HistoryImportInProgressException();
        }
        try {
            return Optional.of(run(source.get()));
        } finally {
            running.remove(accountId);
        }
    }

    private HistoryImportResult run(HistorySource source) {
        Tally tally = new Tally();
        try {
            readAll(source, tally);
        } catch (HistoryPermissionException e) {
            recordFailure(source, HistoryImportFailure.PERMISSION);
            throw e;
        } catch (HistoryFetchException e) {
            recordFailure(source, HistoryImportFailure.FETCH);
            throw e;
        } finally {
            if (tally.imported > 0) {
                publisher.publishEvent(new HistoryImportedEvent(source.organizationId()));
            }
        }
        store.markImported(source.channel(), source.accountId(), clock.instant());
        return new HistoryImportResult(tally.conversations, tally.imported, tally.skipped, tally.truncated);
    }

    private void recordFailure(HistorySource source, HistoryImportFailure failure) {
        store.markFailed(source.channel(), source.accountId(), failure, clock.instant());
    }

    private void readAll(HistorySource source, Tally tally) {
        String token = cipher.decrypt(source.encryptedToken());
        Iterator<HistoryConversation> history = clients.forChannel(source.channel())
                .conversations(source.externalId(), token);
        while (!tally.truncated && history.hasNext()) {
            if (tally.conversations >= limits.maxConversations()) {
                tally.truncated = true;
                break;
            }
            importConversation(source, history.next(), tally);
        }
    }

    private void importConversation(HistorySource source, HistoryConversation conversation, Tally tally) {
        tally.conversations++;
        for (NormalizedMessage message : conversation.messages()) {
            if (tally.imported + tally.skipped >= limits.maxMessages()) {
                tally.truncated = true;
                return;
            }
            if (inbox.importHistorical(source.account(), message)) {
                tally.imported++;
            } else {
                tally.skipped++;
            }
        }
        names.nameIfAbsent(source.organizationId(), source.channel(), conversation.peerId(), conversation.peerName());
        if (conversation.moreMessages()) {
            tally.truncated = true;
        }
    }

    private static final class Tally {
        int conversations;
        int imported;
        int skipped;
        boolean truncated;
    }
}
