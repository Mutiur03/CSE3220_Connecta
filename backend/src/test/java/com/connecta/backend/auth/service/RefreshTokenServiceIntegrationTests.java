package com.connecta.backend.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.connecta.backend.auth.dto.LoginRequest;
import com.connecta.backend.auth.dto.RegisterRequest;
import com.connecta.backend.auth.dto.TokenResponse;
import com.connecta.backend.auth.repository.RefreshTokenRepository;
import com.connecta.backend.support.RandomJwtSecret;
import com.connecta.backend.support.TestClock;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Real PostgreSQL: Flyway applies every migration, Hibernate validates the schema against it
 * (ddl-auto=validate), and refresh-token rotation is persisted. Skipped when Docker is absent.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false"})
@Import(TestClock.class)
@ContextConfiguration(initializers = RandomJwtSecret.class)
class RefreshTokenServiceIntegrationTests {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    AuthService auth;

    @Autowired
    RefreshTokenRepository tokens;

    private TokenResponse registerAndLogin(String email) {
        auth.register(new RegisterRequest(email, "correct-password", "Jane"));
        return auth.login(new LoginRequest(email, "correct-password"));
    }

    private boolean isRevoked(String rawToken) {
        return tokens.findByTokenHash(RefreshTokenService.hash(rawToken)).orElseThrow().isRevoked();
    }

    @Test
    void rotationPersistsARevokedOldTokenAndALiveNewOne() {
        TokenResponse first = registerAndLogin("rotate@example.com");

        TokenResponse second = auth.refresh(first.refreshToken());

        assertNotEquals(first.refreshToken(), second.refreshToken());
        assertTrue(isRevoked(first.refreshToken()));
        assertFalse(isRevoked(second.refreshToken()));
    }

    @Test
    void reusingARotatedTokenRevokesTheWholeSessionAndKeepsTheRevocation() {
        TokenResponse first = registerAndLogin("reuse@example.com");
        TokenResponse second = auth.refresh(first.refreshToken());

        assertThrows(InvalidTokenException.class, () -> auth.refresh(first.refreshToken()));

        assertTrue(isRevoked(second.refreshToken()));
        assertThrows(InvalidTokenException.class, () -> auth.refresh(second.refreshToken()));
    }

    @Test
    void storedExpiryUsesTheRefreshLifetimeFromTheFixedClock() {
        TokenResponse login = registerAndLogin("expiry@example.com");

        var stored = tokens.findByTokenHash(RefreshTokenService.hash(login.refreshToken())).orElseThrow();

        assertEquals(TestClock.NOW.plus(Duration.ofDays(7)), stored.getExpiresAt());
    }
}
