package com.connecta.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on {@code @Scheduled} jobs (the refresh-token cleanup). */
@Configuration
@EnableScheduling
class SchedulingConfig {
}
