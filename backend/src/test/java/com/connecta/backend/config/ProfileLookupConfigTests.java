package com.connecta.backend.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class ProfileLookupConfigTests {

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(ProfileLookupConfig.class);
    private ThreadPoolTaskExecutor executor;

    @BeforeEach
    void attachAppender() {
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void cleanUp() {
        logger.detachAppender(logs);
        if (executor != null) {
            executor.shutdown();
        }
    }

    @Test
    void propertiesBindToDefaults() {
        ProfileLookupProperties p = new ProfileLookupProperties(null, null, null, null, null, null);

        assertEquals(Duration.ofSeconds(3), p.timeout());
        assertEquals(2, p.poolSize());
        assertEquals(100, p.queueCapacity());
        assertEquals(Duration.ofSeconds(10), p.shutdownTimeout());
    }

    @Test
    void executorWaitsForQueuedTasksOnShutdown() {
        executor = build(new ProfileLookupProperties(null, 1, 1, Duration.ofSeconds(7), null, null));

        assertTrue((boolean) org.springframework.test.util.ReflectionTestUtils.getField(
                executor, "waitForTasksToCompleteOnShutdown"));
        assertEquals(7000L, (long) org.springframework.test.util.ReflectionTestUtils.getField(
                executor, "awaitTerminationMillis"));
    }

    @Test
    void fullQueueDropsLookupLogsOnceAndCountsWithoutThrowing() throws InterruptedException {
        executor = build(new ProfileLookupProperties(null, 1, 1, null, null, null));
        CountDownLatch release = new CountDownLatch(1);
        executor.execute(() -> await(release)); // occupies the single worker
        executor.execute(() -> { }); // fills the single queue slot

        assertDoesNotThrow(() -> executor.execute(() -> { }));
        release.countDown();

        assertEquals(1.0, meters.get(ProfileLookupConfig.DROPPED_METRIC).counter().count());
        assertEquals(1, logs.list.size());
        String line = logs.list.get(0).getFormattedMessage();
        assertTrue(line.contains("capacity 1"));
        assertFalse(line.toLowerCase().contains("token"));
    }

    private ThreadPoolTaskExecutor build(ProfileLookupProperties properties) {
        ThreadPoolTaskExecutor e = new ProfileLookupConfig().profileLookupExecutor(properties, meters);
        e.initialize();
        return e;
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
