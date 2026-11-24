package com.connecta.backend.config;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.connecta.backend.auth.service.TokenService;
import com.connecta.backend.support.TestClock;
import com.connecta.backend.support.TestUsers;
import com.connecta.backend.user.domain.Roles;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;

class JwtConfigTests {

    static final String SECRET = "unit-test-secret-unit-test-secret-0123456789";

    private final JwtConfig config = new JwtConfig();

    private static JwtProperties props(String secret, String issuer) {
        return new JwtProperties(secret, issuer, Duration.ofMinutes(15), Duration.ofDays(7));
    }

    private String tokenIssuedAt(Clock clock) {
        JwtProperties props = props(SECRET, "connecta");
        return new TokenService(config.jwtEncoder(props), props, clock)
                .createAccessToken(TestUsers.user("jane@example.com", Roles.AGENT), java.util.UUID.randomUUID());
    }

    private JwtDecoder decoder(String secret, String issuer) {
        return config.jwtDecoder(props(secret, issuer), TestClock.FIXED);
    }

    @Test
    void validTokenIsAccepted() {
        decoder(SECRET, "connecta").decode(tokenIssuedAt(TestClock.FIXED));
    }

    @Test
    void expiredTokenIsRejected() {
        String old = tokenIssuedAt(Clock.fixed(TestClock.NOW.minus(Duration.ofHours(1)), ZoneOffset.UTC));

        assertThrows(JwtValidationException.class, () -> decoder(SECRET, "connecta").decode(old));
    }

    @Test
    void tokenThatExpiredHalfAMinuteAgoIsRejected() {
        // Spring's default 60 s leeway would still accept this one.
        Instant issued = TestClock.NOW.minus(Duration.ofMinutes(15)).minus(Duration.ofSeconds(30));
        String justExpired = tokenIssuedAt(Clock.fixed(issued, ZoneOffset.UTC));

        assertThrows(JwtValidationException.class, () -> decoder(SECRET, "connecta").decode(justExpired));
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        String token = tokenIssuedAt(TestClock.FIXED);

        assertThrows(JwtException.class,
                () -> decoder("another-secret-another-secret-0123456789", "connecta").decode(token));
    }

    @Test
    void tokenFromAnotherIssuerIsRejected() {
        String token = tokenIssuedAt(TestClock.FIXED);

        assertThrows(JwtValidationException.class, () -> decoder(SECRET, "someone-else").decode(token));
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = tokenIssuedAt(TestClock.FIXED);
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        assertThrows(JwtException.class, () -> decoder(SECRET, "connecta").decode(tampered));
    }

    @Test
    void secretShorterThan32BytesIsRefused() {
        assertThrows(IllegalStateException.class, () -> config.jwtEncoder(props("too-short", "connecta")));
        assertThrows(IllegalStateException.class, () -> decoder("too-short", "connecta"));
    }
}
