package com.connecta.backend.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** One fixed instant for every test, so nothing depends on the wall clock. */
@TestConfiguration(proxyBeanMethods = false)
public class TestClock {

    public static final Instant NOW = Instant.parse("2026-11-15T12:00:00Z");
    public static final Clock FIXED = Clock.fixed(NOW, ZoneOffset.UTC);

    /** Wins over the application's system clock in Spring tests. */
    @Bean
    @Primary
    Clock fixedClock() {
        return FIXED;
    }
}
