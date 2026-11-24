package com.connecta.backend.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.connecta.backend.auth.dto.ClientInfo;
import com.connecta.backend.auth.dto.LoginRequest;
import com.connecta.backend.auth.dto.RegisterRequest;
import com.connecta.backend.auth.dto.TokenResponse;
import com.connecta.backend.auth.repository.RefreshTokenRepository;
import com.connecta.backend.setup.service.SetupService;
import com.connecta.backend.support.RandomJwtSecret;
import com.connecta.backend.support.TestClock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Real PostgreSQL: a password change persists the new hash and revokes the other sessions. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false"})
@Import(TestClock.class)
@ContextConfiguration(initializers = RandomJwtSecret.class)
class PasswordServiceIntegrationTests {

    static final String OLD = "old-password-123";
    static final String NEW = "new-password-456";

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
    PasswordService passwords;

    @Autowired
    SessionService sessions;

    @Autowired
    RefreshTokenRepository tokens;

    @Autowired
    SetupService setup;

    private TokenResponse register(String email) {
        if (setup.status().setupRequired()) {
            setup.completeSetup(new RegisterRequest("owner@example.com", "correct-password", "Owner"));
        }
        auth.register(new RegisterRequest(email, OLD, "Jane"));
        return login(email, OLD);
    }

    private TokenResponse login(String email, String password) {
        return auth.login(new LoginRequest(email, password), ClientInfo.UNKNOWN);
    }

    private java.util.UUID sessionOf(String rawRefreshToken) {
        return tokens.findByTokenHash(RefreshTokenService.hash(rawRefreshToken)).orElseThrow().getSessionId();
    }

    @Test
    void changingThePasswordKeepsThisSessionAndEndsTheOthers() {
        TokenResponse thisDevice = register("pw-change@example.com");
        TokenResponse otherDevice = login("pw-change@example.com", OLD);
        var userId = thisDevice.user().id();

        passwords.change(userId, sessionOf(thisDevice.refreshToken()), OLD, NEW);

        assertThrows(InvalidTokenException.class, () -> auth.refresh(otherDevice.refreshToken(), ClientInfo.UNKNOWN));
        auth.refresh(thisDevice.refreshToken(), ClientInfo.UNKNOWN);
        assertEquals(1, sessions.activeSessions(userId, PageRequest.of(0, 10)).getTotalElements());
    }

    @Test
    void theNewPasswordLogsInAndTheOldOneDoesNot() {
        TokenResponse session = register("pw-login@example.com");

        passwords.change(session.user().id(), sessionOf(session.refreshToken()), OLD, NEW);

        login("pw-login@example.com", NEW);
        assertThrows(InvalidCredentialsException.class, () -> login("pw-login@example.com", OLD));
    }

    @Test
    void aWrongCurrentPasswordChangesNothing() {
        TokenResponse session = register("pw-wrong@example.com");

        assertThrows(WrongPasswordException.class,
                () -> passwords.change(session.user().id(), sessionOf(session.refreshToken()), "not-the-password", NEW));

        login("pw-wrong@example.com", OLD);
    }
}
