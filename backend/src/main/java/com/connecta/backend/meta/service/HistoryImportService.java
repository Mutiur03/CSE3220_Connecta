package com.connecta.backend.meta.service;

import com.connecta.backend.config.HistoryImportProperties;
import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.inbox.service.HistoryImportedEvent;
import com.connecta.backend.inbox.service.HistoryMessageOutcome;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Reconciles one connected account past conversations with the inbox (idempotent, tenant scoped): a message the
 * organization lacks is stored, one it has is brought in line with Meta (body corrected, missing attachments
 * added), and nothing absent from Meta is deleted. Meta is read page by page and every message is stored in its own short transaction, so no connection
 * is held across a Meta call and a failure part-way keeps what was stored; running again continues. Conversations
 * are read newest first, each stored oldest message first so the conversation last activity ends up right.
 * Stops at the configured limits and then reports {@code truncated}. After a successful run the account is marked
 * imported and one {@link HistoryImportedEvent} is published (no per-message pushes), also when nothing changed, so
 * customers still without a picture get looked up on a reconnect; a failed run publishes it only when it inserted
 * or updated something.
 *
 * <p>Only one import per account runs at a time: the guard is in memory, so it holds for a single server
 * instance. A second instance would only repeat work, since storing is idempotent.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class HistoryImportService {

    /** A progress line is logged after this many conversations, so a long import is never silent. */
    private static final int PROGRESS_EVERY = 10;

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
        return read(channel, accountId, HistoryBounds.of(limits), true);
    }

    /**
     * Template Method over the import: the same read and idempotent store with smaller bounds, as the safety
     * net behind the webhook. It never marks the account imported or failed (a small read says nothing about
     * the full history); on success it records the sync time, and it announces new messages only when it
     * stored some.
     *
     * @throws HistoryImportInProgressException when this account is already being imported or synced
     */
    public Optional<HistoryImportResult> syncRecent(Channel channel, UUID accountId, HistoryBounds bounds) {
        return read(channel, accountId, bounds, false);
    }

    private Optional<HistoryImportResult> read(Channel channel, UUID accountId, HistoryBounds bounds,
            boolean full) {
        Optional<HistorySource> source = store.find(channel, accountId);
        if (source.isEmpty()) {
            return Optional.empty();
        }
        if (!running.add(accountId)) {
            throw new HistoryImportInProgressException();
        }
        try {
            return Optional.of(full ? run(source.get(), bounds) : runSync(source.get(), bounds));
        } finally {
            running.remove(accountId);
        }
    }

    private HistoryImportResult runSync(HistorySource source, HistoryBounds bounds) {
        Tally tally = new Tally();
        try {
            readAll(source, tally, bounds);
        } finally {
            if (tally.changed() > 0) {
                publisher.publishEvent(new HistoryImportedEvent(source.organizationId()));
            }
        }
        if (tally.limitReached) {
            log.warn("Message sync of {} account {} stopped at its safety ceiling; older changes may be missing",
                    source.channel(), source.accountId());
        }
        store.markSynced(source.channel(), source.accountId(), clock.instant());
        return tally.result();
    }

    private HistoryImportResult run(HistorySource source, HistoryBounds bounds) {
        Tally tally = new Tally();
        boolean completed = false;
        try {
            readAll(source, tally, bounds);
            completed = true;
        } catch (HistoryPermissionException e) {
            recordFailure(source, HistoryImportFailure.PERMISSION);
            throw e;
        } catch (HistoryFetchException e) {
            recordFailure(source, HistoryImportFailure.FETCH);
            throw e;
        } finally {
            if (completed || tally.changed() > 0) {
                publisher.publishEvent(new HistoryImportedEvent(source.organizationId()));
            }
        }
        store.markImported(source.channel(), source.accountId(), clock.instant());
        return tally.result();
    }

    private void recordFailure(HistorySource source, HistoryImportFailure failure) {
        store.markFailed(source.channel(), source.accountId(), failure, clock.instant());
    }

    private void readAll(HistorySource source, Tally tally, HistoryBounds bounds) {
        String token = cipher.decrypt(source.encryptedToken());
        Iterator<HistoryConversation> history = clients.forChannel(source.channel())
                .conversations(source.externalId(), token, bounds);
        while (!tally.limitReached && history.hasNext()) {
            if (tally.conversations >= bounds.maxConversations()) {
                tally.stop();
                break;
            }
            importConversation(source, history.next(), tally, bounds);
            if (tally.conversations % PROGRESS_EVERY == 0) {
                log.info("History import of {} account {}: {} conversations read so far", source.channel(),
                        source.accountId(), tally.conversations);
            }
        }
    }

    private void importConversation(HistorySource source, HistoryConversation conversation, Tally tally,
            HistoryBounds bounds) {
        tally.conversations++;
        for (NormalizedMessage message : conversation.messages()) {
            if (tally.changed() + tally.unchanged >= bounds.maxMessages()) {
                tally.stop();
                return;
            }
            tally.count(inbox.importHistorical(source.account(), message));
        }
        names.nameIfAbsent(source.organizationId(), source.channel(), conversation.peerId(), conversation.peerName());
        if (conversation.moreMessages()) {
            // Only this chat has more history than the per-conversation cap; the other chats are still imported.
            tally.truncated = true;
        }
    }

    private static final class Tally {
        int conversations;
        int inserted;
        int updated;
        int unchanged;
        /** Something was left behind: a cap was hit, or a chat has more messages than we keep. */
        boolean truncated;
        /** A cap that covers the whole import was hit, so no further conversation is read. */
        boolean limitReached;

        void stop() {
            truncated = true;
            limitReached = true;
        }

        void count(HistoryMessageOutcome outcome) {
            switch (outcome) {
                case INSERTED -> inserted++;
                case UPDATED -> updated++;
                case UNCHANGED -> unchanged++;
            }
        }

        HistoryImportResult result() {
            return new HistoryImportResult(conversations, inserted, updated, unchanged, truncated);
        }

        int changed() {
            return inserted + updated;
        }
    }
}
