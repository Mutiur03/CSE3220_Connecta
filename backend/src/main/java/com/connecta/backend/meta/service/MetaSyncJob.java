package com.connecta.backend.meta.service;

import com.connecta.backend.config.MetaSyncConfig;
import com.connecta.backend.config.MetaSyncProperties;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Safety net behind the webhook: at startup (this closes a downtime gap) and then every app.meta.sync.interval,
 * reads the newest messages of every connected Page and Instagram account and stores what is missing, reusing
 * {@link HistoryImportService}. The batch runs on its own executor so it never holds the scheduler thread, one
 * failing account never stops the others, and an account being imported is skipped. Logs counts and error
 * classes only, never tokens or message text.
 */
@Slf4j
@Component
class MetaSyncJob {

    private final HistoryImportService imports;
    private final HistoryAccountStore store;
    private final MetaSyncProperties properties;
    private final Executor executor;

    MetaSyncJob(HistoryImportService imports, HistoryAccountStore store, MetaSyncProperties properties,
            @Qualifier(MetaSyncConfig.EXECUTOR) Executor executor) {
        this.imports = imports;
        this.store = store;
        this.properties = properties;
        this.executor = executor;
    }

    @EventListener(ApplicationReadyEvent.class)
    void onStartup() {
        sync();
    }

    @Scheduled(fixedDelayString = "${app.meta.sync.interval:10m}",
            initialDelayString = "${app.meta.sync.interval:10m}")
    void sync() {
        if (!properties.enabled()) {
            return;
        }
        try {
            executor.execute(this::syncAll);
        } catch (RejectedExecutionException e) {
            log.info("Message sync skipped: the previous run is still going");
        }
    }

    void syncAll() {
        List<PendingHistoryImport> accounts;
        try {
            accounts = store.connected();
        } catch (RuntimeException e) {
            log.warn("Message sync could not list accounts: {}", e.getClass().getSimpleName());
            return;
        }
        int stored = 0;
        int failed = 0;
        for (PendingHistoryImport account : accounts) {
            try {
                stored += imports.syncRecent(account.channel(), account.accountId(), properties.bounds())
                        .map(HistoryImportResult::messagesInserted).orElse(0);
            } catch (HistoryImportInProgressException e) {
                log.debug("Message sync skipped for {} account {}: import running", account.channel(),
                        account.accountId());
            } catch (RuntimeException e) {
                failed++;
                log.warn("Message sync of {} account {} failed: {}", account.channel(), account.accountId(),
                        e.getClass().getSimpleName());
            }
        }
        log.info("Message sync: {} accounts, {} stored, {} failed", accounts.size(), stored, failed);
    }
}
