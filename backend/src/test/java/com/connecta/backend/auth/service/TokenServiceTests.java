package com.connecta.backend.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.connecta.backend.auth.security.JwtProperties;
import com.connecta.backend.config.JwtConfig;
import com.connecta.backend.support.TestClock;
import com.connecta.backend.support.TestUsers;
import com.connecta.backend.user.domain.Roles;
import com.connecta.backend.user.domain.User;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class TokenServiceTests {

    static final JwtProperties PROPS = new JwtProperties(
            "unit-test-secret-unit-test-secret-0123456789", "connecta", Duration.ofMinutes(15), Duration.ofDays(7));

    private final JwtConfig config = new JwtConfig();

    private TokenService service(Clock clock) {
        return new TokenService(config.jwtEncoder(PROPS), PROPS, clock);
    }

    @Test
    void accessTokenCarriesSubjectEmailRolesAndLifetime() {
        Clock clock = TestClock.FIXED;
        User user = TestUsers.user("jane@example.com", Roles.ADMIN, Roles.AGENT);
        TokenService service = service(clock);

        Jwt jwt = config.jwtDecoder(PROPS, clock).decode(service.createAccessToken(user));

        assertEquals(user.getId().toString(), jwt.getSubject());
        assertEquals("jane@example.com", jwt.getClaimAsString("email"));
        assertEquals(List.of("ADMIN", "AGENT"), jwt.getClaimAsStringList("roles"));
        assertEquals("connecta", jwt.getClaimAsString("iss"));
        assertEquals(Duration.ofMinutes(15), Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()));
        assertEquals(900L, service.accessTtlSeconds());
    }

    @Test
    void everyTokenHasItsOwnId() {
        TokenService service = service(TestClock.FIXED);
        User user = TestUsers.user("jane@example.com", Roles.AGENT);

        String a = service.createAccessToken(user);
        String b = service.createAccessToken(user);

        assertNotEquals(a, b);
    }
}
