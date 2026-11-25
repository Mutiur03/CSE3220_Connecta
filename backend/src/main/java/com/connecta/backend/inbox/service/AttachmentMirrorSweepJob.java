package com.connecta.backend.inbox.service;

import com.connecta.backend.config.AttachmentMirrorConfig;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Retries copies that failed or were dropped: every app.attachments.mirror-sweep-delay, one bounded batch of
 * attachments without a copy and younger than app.attachments.mirror-max-age. The batch runs on the mirror
 * executor, so downloads never hold the scheduler thread.
 */
@Slf4j
@Component
public class AttachmentMirrorSweepJob {

    private final AttachmentMirrorService mirror;
    private final Executor executor;

    public AttachmentMirrorSweepJob(AttachmentMirrorService mirror,
            @Qualifier(AttachmentMirrorConfig.EXECUTOR) Executor executor) {
        this.mirror = mirror;
        this.executor = executor;
    }

    @Scheduled(fixedDelayString = "${app.attachments.mirror-sweep-delay:5m}",
            initialDelayString = "${app.attachments.mirror-sweep-delay:5m}")
    public void sweep() {
        executor.execute(this::copyBatch);
    }

    private void copyBatch() {
        try {
            int copied = mirror.mirrorPending();
            if (copied > 0) {
                log.info("Attachment sweep copied {} files", copied);
            }
        } catch (RuntimeException e) {
            log.warn("Attachment sweep failed: {}", e.getClass().getSimpleName());
        }
    }
}
