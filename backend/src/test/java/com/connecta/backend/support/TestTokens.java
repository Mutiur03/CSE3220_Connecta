package com.connecta.backend.support;

import com.connecta.backend.config.JwtProperties;
import com.connecta.backend.auth.service.TokenService;
import com.connecta.backend.config.JwtConfig;
import com.connecta.backend.user.domain.User;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.UUID;

/** Builds real signed access tokens for tests, valid or expired, with the app's own encoder. */
public final class TestTokens {

    private TestTokens() {
    }

    public static String bearer(JwtProperties props, User user) {
        return bearer(props, user, UUID.randomUUID());
    }

    /** A token that belongs to a known session, for tests that care which device is current. */
    public static String bearer(JwtProperties props, User user, UUID sessionId) {
        return "Bearer " + token(props, user, TestClock.FIXED, sessionId);
    }

    public static String expiredBearer(JwtProperties props, User user) {
        Clock anHourAgo = Clock.fixed(TestClock.NOW.minus(Duration.ofHours(1)), ZoneOffset.UTC);
        return "Bearer " + token(props, user, anHourAgo, UUID.randomUUID());
    }

    /** A token signed with a different secret than the application uses. */
    public static String forgedBearer(JwtProperties real, User user) {
        JwtProperties forged = new JwtProperties("a-different-secret-a-different-secret-0123456789",
                real.issuer(), real.accessTtl(), real.refreshTtl());
        return "Bearer " + token(forged, user, TestClock.FIXED, UUID.randomUUID());
    }

    private static String token(JwtProperties props, User user, Clock clock, UUID sessionId) {
        return new TokenService(new JwtConfig().jwtEncoder(props), props, clock).createAccessToken(user, sessionId);
    }
}
