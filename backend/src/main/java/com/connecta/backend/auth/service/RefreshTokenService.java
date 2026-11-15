package com.connecta.backend.auth.service;

import com.connecta.backend.auth.domain.RefreshToken;
import com.connecta.backend.auth.repository.RefreshTokenRepository;
import com.connecta.backend.auth.security.JwtProperties;
import com.connecta.backend.user.domain.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opaque, random, single-use refresh tokens. Only a SHA-256 hash is stored. Each use rotates the
 * token; presenting an already-used token means it leaked, so every session of that user is revoked.
 */
@Service
public class RefreshTokenService {

    /** A freshly issued raw token plus the user it belongs to. */
    public record Issued(User user, String rawToken) {
    }

    private final RefreshTokenRepository tokens;
    private final JwtProperties props;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository tokens, JwtProperties props, Clock clock) {
        this.tokens = tokens;
        this.props = props;
        this.clock = clock;
    }

    @Transactional
    public String issue(User user) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = clock.instant();
        tokens.save(new RefreshToken(user, hash(raw), now.plus(props.refreshTtl()), now));
        return raw;
    }

    // noRollbackFor: the revocations below must be committed even though we then reject the request.
    @Transactional(noRollbackFor = InvalidTokenException.class)
    public Issued rotate(String rawToken) {
        Instant now = clock.instant();
        RefreshToken stored = tokens.findByTokenHash(hash(rawToken)).orElseThrow(InvalidTokenException::new);
        User user = stored.getUser();
        if (stored.isRevoked()) {
            tokens.revokeAllForUser(user.getId(), now);
            throw new InvalidTokenException();
        }
        if (stored.isExpired(now) || !user.isEnabled()) {
            stored.revoke(now);
            throw new InvalidTokenException();
        }
        stored.revoke(now);
        return new Issued(user, issue(user));
    }

    /** Logout. Unknown or already revoked tokens are ignored, so it is safe to call twice. */
    @Transactional
    public void revoke(String rawToken) {
        tokens.findByTokenHash(hash(rawToken)).ifPresent(t -> t.revoke(clock.instant()));
    }

    static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
