package com.connecta.backend.auth.service;

import static org.mockito.Mockito.verify;

import com.connecta.backend.auth.repository.RefreshTokenRepository;
import com.connecta.backend.support.TestClock;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SessionServiceTests {

    @Mock
    RefreshTokenRepository tokens;

    @Test
    void touchStampsTheSessionOnlyWhenItsLastStampIsOlderThanTheInterval() {
        UUID user = UUID.randomUUID();
        UUID session = UUID.randomUUID();

        new SessionService(tokens, TestClock.FIXED).touch(user, session);

        verify(tokens).touchSession(user, session, TestClock.NOW, TestClock.NOW.minus(SessionService.ACTIVITY_INTERVAL));
    }
}
