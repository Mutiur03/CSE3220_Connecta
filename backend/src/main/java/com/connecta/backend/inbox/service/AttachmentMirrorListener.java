package com.connecta.backend.inbox.service;

import com.connecta.backend.config.AttachmentMirrorConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Observer: once a received message is committed, copies its attachments; after a history import, copies one
 * bounded batch of the organization's pending ones (the sweep does the rest). Runs on the bounded mirror executor,
 * never inside the webhook transaction, and never throws.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AttachmentMirrorListener {

    private final AttachmentMirrorService mirror;

    @Async(AttachmentMirrorConfig.EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageReceived(MessageReceivedEvent event) {
        try {
            mirror.mirrorMessage(event.organizationId(), event.messageId());
        } catch (RuntimeException e) {
            log.warn("Copying the attachments of message {} failed: {}", event.messageId(),
                    e.getClass().getSimpleName());
        }
    }

    @Async(AttachmentMirrorConfig.EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onHistoryImported(HistoryImportedEvent event) {
        try {
            mirror.mirrorPending(event.organizationId());
        } catch (RuntimeException e) {
            log.warn("Copying imported attachments failed: {}", e.getClass().getSimpleName());
        }
    }
}
