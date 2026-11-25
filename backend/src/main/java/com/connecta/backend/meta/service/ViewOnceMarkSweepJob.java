package com.connecta.backend.meta.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Deletes view-once marks older than app.meta.view-once.mark-ttl, every app.meta.view-once.cleanup-interval. */
@Slf4j
@Component
@RequiredArgsConstructor
class ViewOnceMarkSweepJob {

    private final ViewOnceService viewOnce;

    @Scheduled(fixedDelayString = "${app.meta.view-once.cleanup-interval:10m}",
            initialDelayString = "${app.meta.view-once.cleanup-interval:10m}")
    void sweep() {
        try {
            int deleted = viewOnce.deleteExpiredMarks();
            if (deleted > 0) {
                log.debug("Deleted {} expired view-once marks", deleted);
            }
        } catch (RuntimeException e) {
            log.warn("View-once mark cleanup failed: {}", e.getClass().getSimpleName());
        }
    }
}
