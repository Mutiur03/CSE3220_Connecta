package com.connecta.backend.auth.service;

import org.springframework.data.domain.PageRequest;
import com.connecta.backend.auth.dto.ClientInfo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.connecta.backend.auth.dto.LoginRequest;
import com.connecta.backend.auth.dto.RegisterRequest;
import com.connecta.backend.auth.dto.TokenResponse;
import com.connecta.backend.setup.service.SetupService;
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

    @Autowired
    SetupService setup;

    @Autowired
    SessionService sessionService;

    private TokenResponse registerAndLogin(String email) {
        if (setup.status().setupRequired()) {
            setup.completeSetup(new RegisterRequest("owner@example.com", "correct-password", "Owner"));
        }
        auth.register(new RegisterRequest(email, "correct-password", "Jane"));
        return auth.login(new LoginRequest(email, "correct-password"), ClientInfo.UNKNOWN);
    }

    private boolean isRevoked(String rawToken) {
        return tokens.findByTokenHash(RefreshTokenService.hash(rawToken)).orElseThrow().isRevoked();
    }

    @Test
    void rotationPersistsARevokedOldTokenAndALiveNewOne() {
        TokenResponse first = registerAndLogin("rotate@example.com");

        TokenResponse second = auth.refresh(first.refreshToken(), ClientInfo.UNKNOWN);

        assertNotEquals(first.refreshToken(), second.refreshToken());
        assertTrue(isRevoked(first.refreshToken()));
        assertFalse(isRevoked(second.refreshToken()));
    }

    @Test
    void reusingARotatedTokenRevokesTheWholeSessionAndKeepsTheRevocation() {
        TokenResponse first = registerAndLogin("reuse@example.com");
        TokenResponse second = auth.refresh(first.refreshToken(), ClientInfo.UNKNOWN);

        assertThrows(InvalidTokenException.class, () -> auth.refresh(first.refreshToken(), ClientInfo.UNKNOWN));

        assertTrue(isRevoked(second.refreshToken()));
        assertThrows(InvalidTokenException.class, () -> auth.refresh(second.refreshToken(), ClientInfo.UNKNOWN));
    }

    @Test
    void rotationKeepsOneSessionPerDeviceAndRevokingItEndsTheChain() {
        TokenResponse first = registerAndLogin("sessions@example.com");
        TokenResponse second = auth.refresh(first.refreshToken(), ClientInfo.UNKNOWN);
        var userId = first.user().id();
        var sessionId = tokens.findByTokenHash(RefreshTokenService.hash(second.refreshToken())).orElseThrow().getSessionId();

        var active = sessionService.activeSessions(userId, PageRequest.of(0, 10));
        assertEquals(1, active.getTotalElements());
        assertEquals(sessionId, active.getContent().get(0).getSessionId());

        sessionService.revoke(userId, sessionId);

        assertThrows(InvalidTokenException.class, () -> auth.refresh(second.refreshToken(), ClientInfo.UNKNOWN));
        assertThrows(SessionNotFoundException.class, () -> sessionService.revoke(userId, sessionId));
    }

    @Test
    void storedExpiryUsesTheRefreshLifetimeFromTheFixedClock() {
        TokenResponse login = registerAndLogin("expiry@example.com");

        var stored = tokens.findByTokenHash(RefreshTokenService.hash(login.refreshToken())).orElseThrow();

        assertEquals(TestClock.NOW.plus(Duration.ofDays(7)), stored.getExpiresAt());
    }
}
