package com.connecta.backend.auth.service;

import com.connecta.backend.auth.dto.ClientInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.connecta.backend.auth.domain.RefreshToken;
import com.connecta.backend.auth.repository.RefreshTokenRepository;
import com.connecta.backend.config.JwtProperties;
import com.connecta.backend.user.domain.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opaque, random, single-use refresh tokens. Only a SHA-256 hash is stored. Each use rotates the
 * token; presenting an already-used token means it leaked, so every session of that user is revoked.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RefreshTokenService {

    /** A freshly issued raw token, the user it belongs to and the session (device) it continues. */
    public record Issued(User user, String rawToken, UUID sessionId) {
    }

    private static final int TOKEN_BYTES = 32;
    private static final String HASH_ALGORITHM = "SHA-256";

    private final RefreshTokenRepository tokens;
    private final JwtProperties props;
    private final Clock clock;
    private final IpLocator ipLocator;
    private final SecureRandom random = new SecureRandom();

    /** Starts a new session (a login). */
    @Transactional
    public Issued issue(User user, ClientInfo client) {
        return issue(user, UUID.randomUUID(), client);
    }

    private Issued issue(User user, UUID sessionId, ClientInfo client) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = clock.instant();
        tokens.save(new RefreshToken(user, hash(raw), now.plus(props.refreshTtl()), now,
                sessionId, client.userAgent(), client.ip(),
                ipLocator.locate(client.ip()).orElse(null)));
        return new Issued(user, raw, sessionId);
    }

    // noRollbackFor: the revocations below must be committed even though we then reject the request.
    @Transactional(noRollbackFor = InvalidTokenException.class)
    public Issued rotate(String rawToken, ClientInfo client) {
        Instant now = clock.instant();
        RefreshToken stored = tokens.findByTokenHash(hash(rawToken)).orElseThrow(InvalidTokenException::new);
        User user = stored.getUser();
        if (stored.isRevoked()) {
            // Only a rotated token was ever handed on, so only its replay means a leak. A token revoked by
            // logout, "sign out this device" or a password change has no successor: a plain rejection,
            // so a signed-out device cannot take the user's other sessions down with it.
            if (wasRotated(stored)) {
                log.warn("Refresh token reuse detected, revoking all sessions of user {}", user.getId());
                tokens.revokeAllForUser(user.getId(), now);
            }
            throw new InvalidTokenException();
        }
        if (stored.isExpired(now) || !user.isEnabled()) {
            stored.revoke(now);
            throw new InvalidTokenException();
        }
        stored.revoke(now);
        return issue(user, stored.getSessionId(), client);
    }

    private boolean wasRotated(RefreshToken token) {
        return tokens.existsBySessionIdAndCreatedAtGreaterThanEqualAndIdNot(
                token.getSessionId(), token.getCreatedAt(), token.getId());
    }

    /** Logout. Unknown or already revoked tokens are ignored, so it is safe to call twice. */
    @Transactional
    public void revoke(String rawToken) {
        tokens.findByTokenHash(hash(rawToken)).ifPresent(t -> t.revoke(clock.instant()));
    }

    static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance(HASH_ALGORITHM).digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
