package com.connecta.backend.auth.service;

import static org.mockito.Mockito.verify;

import com.connecta.backend.auth.repository.RefreshTokenRepository;
import com.connecta.backend.support.TestClock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefreshTokenCleanupJobTests {

    @Mock
    RefreshTokenRepository tokens;

    @Test
    void deletesTokensThatExpiredBeforeTheGracePeriod() {
        new RefreshTokenCleanupJob(tokens, TestClock.FIXED).deleteLongExpiredTokens();

        verify(tokens).deleteByExpiresAtBefore(TestClock.NOW.minus(RefreshTokenCleanupJob.GRACE_PERIOD));
    }
}
