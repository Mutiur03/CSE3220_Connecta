package com.connecta.backend.meta.service;

import com.connecta.backend.config.HistoryImportProperties;
import com.connecta.backend.inbox.domain.Channel;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Runs history imports off the request thread on a small bounded pool. A full queue drops the job (the startup
 * catch-up retries it, since the account stays unmarked); a failed import is logged by type only, never with a
 * token, a Meta error text or customer data. Nothing here ever throws to the caller.
 */
@Slf4j
@Component
class HistoryImportScheduler {

    private final HistoryImportService imports;
    private final Executor executor;

    @Autowired
    HistoryImportScheduler(HistoryImportService imports, HistoryImportProperties limits) {
        this(imports, new ThreadPoolExecutor(limits.workerThreads(), limits.workerThreads(), 0L,
                TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(limits.queueCapacity()), daemonThreads(),
                new ThreadPoolExecutor.AbortPolicy()));
    }

    /** The executor is a parameter so tests can run jobs inline. */
    HistoryImportScheduler(HistoryImportService imports, Executor executor) {
        this.imports = imports;
        this.executor = executor;
    }

    /** Imports one account in the background. */
    void submit(Channel channel, UUID accountId) {
        submitAll(List.of(new PendingHistoryImport(channel, accountId)));
    }

    /** Imports the accounts one after another in one background job. */
    void submitAll(List<PendingHistoryImport> pending) {
        if (pending.isEmpty()) {
            return;
        }
        try {
            executor.execute(() -> pending.forEach(this::runOne));
        } catch (RejectedExecutionException e) {
            log.warn("History import queue is full; {} import(s) wait for the next start", pending.size());
        }
    }

    private void runOne(PendingHistoryImport job) {
        try {
            imports.importHistory(job.channel(), job.accountId()).ifPresent(result -> log.info(
                    "History import of {} account {}: {} conversations, {} messages inserted, {} updated,"
                            + " {} unchanged, truncated={}",
                    job.channel(), job.accountId(), result.conversations(), result.messagesInserted(),
                    result.messagesUpdated(), result.messagesUnchanged(), result.truncated()));
        } catch (RuntimeException e) {
            log.warn("History import of {} account {} did not finish: {}", job.channel(), job.accountId(),
                    e.getClass().getSimpleName());
        }
    }

    @PreDestroy
    void shutdown() {
        if (executor instanceof ExecutorService service) {
            service.shutdownNow();
        }
    }

    private static ThreadFactory daemonThreads() {
        return runnable -> {
            Thread thread = new Thread(runnable, "history-import");
            thread.setDaemon(true);
            return thread;
        };
    }
}
