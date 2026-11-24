package com.connecta.backend.auth.service;

import com.connecta.backend.auth.repository.RefreshTokenRepository;
import java.time.Clock;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes refresh tokens that expired long ago. Revoked rows are kept until they expire (plus the grace
 * period) because reuse detection needs them; once expired they can no longer be replayed.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RefreshTokenCleanupJob {

    static final Duration GRACE_PERIOD = Duration.ofDays(30);

    private final RefreshTokenRepository tokens;
    private final Clock clock;

    @Scheduled(cron = "${connecta.auth.token-cleanup-cron:0 0 3 * * *}")
    @Transactional
    public void deleteLongExpiredTokens() {
        int deleted = tokens.deleteByExpiresAtBefore(clock.instant().minus(GRACE_PERIOD));
        log.info("Deleted {} long-expired refresh tokens", deleted);
    }
}
