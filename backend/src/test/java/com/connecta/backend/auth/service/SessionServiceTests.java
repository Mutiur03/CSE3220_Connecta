package com.connecta.backend.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.connecta.backend.auth.domain.RefreshToken;
import com.connecta.backend.auth.repository.RefreshTokenRepository;
import com.connecta.backend.support.TestClock;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class SessionServiceTests {

    static final UUID USER = UUID.randomUUID();
    static final UUID SESSION = UUID.randomUUID();

    @Mock
    RefreshTokenRepository tokens;

    SessionService service() {
        return new SessionService(tokens, TestClock.FIXED);
    }

    @Nested
    class ActiveSessions {

        @Test
        void listsOnlyTheCallersLiveTokensAtTheCurrentTime() {
            Pageable pageable = PageRequest.of(0, 20);
            Page<RefreshToken> page = new PageImpl<>(List.of());
            when(tokens.findByUserIdAndRevokedAtIsNullAndExpiresAtAfter(USER, TestClock.NOW, pageable)).thenReturn(page);

            assertEquals(page, service().activeSessions(USER, pageable));
        }
    }

    @Nested
    class Revoke {

        @Test
        void revokesTheCallersSession() {
            when(tokens.revokeSession(USER, SESSION, TestClock.NOW)).thenReturn(1);

            service().revoke(USER, SESSION);

            verify(tokens).revokeSession(USER, SESSION, TestClock.NOW);
        }

        @Test
        void unknownOrForeignSessionIsNotFound() {
            when(tokens.revokeSession(USER, SESSION, TestClock.NOW)).thenReturn(0);

            assertThrows(SessionNotFoundException.class, () -> service().revoke(USER, SESSION));
        }
    }

    @Nested
    class RevokeOthers {

        @Test
        void revokesEverySessionButTheCurrentOne() {
            service().revokeOthers(USER, SESSION);

            verify(tokens).revokeOtherSessions(USER, SESSION, TestClock.NOW);
        }
    }

    @Nested
    class RevokeAll {

        @Test
        void revokesEverySessionIncludingTheCurrentOne() {
            service().revokeAll(USER);

            verify(tokens).revokeAllForUser(USER, TestClock.NOW);
        }
    }
}
