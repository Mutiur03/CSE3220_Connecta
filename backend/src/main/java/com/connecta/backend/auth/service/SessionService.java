package com.connecta.backend.auth.service;

import com.connecta.backend.auth.domain.RefreshToken;
import com.connecta.backend.auth.repository.RefreshTokenRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A user's own signed-in devices. Every call is scoped to the user id, so other users' sessions are invisible. */
@Service
@RequiredArgsConstructor
public class SessionService {

    /** At most one write per session in this time, so a busy page does not turn into a write per request. */
    static final Duration ACTIVITY_INTERVAL = Duration.ofMinutes(1);

    private final RefreshTokenRepository tokens;
    private final Clock clock;

    /** Rotation revokes the old token, so each live session has exactly one active token row. */
    @Transactional(readOnly = true)
    public Page<RefreshToken> activeSessions(UUID userId, Pageable pageable) {
        return tokens.findByUserIdAndRevokedAtIsNullAndExpiresAtAfter(userId, clock.instant(), pageable);
    }

    /** Records that the session just made a request. A revoked or unknown session is left alone. */
    @Transactional
    public void touch(UUID userId, UUID sessionId) {
        Instant now = clock.instant();
        tokens.touchSession(userId, sessionId, now, now.minus(ACTIVITY_INTERVAL));
    }

    @Transactional
    public void revoke(UUID userId, UUID sessionId) {
        if (tokens.revokeSession(userId, sessionId, clock.instant()) == 0) {
            throw new SessionNotFoundException();
        }
    }

    /** Every session of the user, including the caller's own. */
    @Transactional
    public void revokeAll(UUID userId) {
        tokens.revokeAllForUser(userId, clock.instant());
    }

    /** "Log out everywhere else". Succeeds with nothing to revoke. */
    @Transactional
    public void revokeOthers(UUID userId, UUID currentSessionId) {
        tokens.revokeOtherSessions(userId, currentSessionId, clock.instant());
    }
}
