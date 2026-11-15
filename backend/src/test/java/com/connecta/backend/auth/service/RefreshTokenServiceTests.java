package com.connecta.backend.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.connecta.backend.auth.domain.RefreshToken;
import com.connecta.backend.auth.repository.RefreshTokenRepository;
import com.connecta.backend.config.JwtProperties;
import com.connecta.backend.support.TestUsers;
import com.connecta.backend.user.domain.Roles;
import com.connecta.backend.user.domain.User;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTests {

    static final Instant NOW = Instant.parse("2026-11-15T12:00:00Z");

    @Mock
    RefreshTokenRepository tokens;

    RefreshTokenService service;
    User user;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties(
                "unit-test-secret-unit-test-secret-0123456789", "connecta", Duration.ofMinutes(15), Duration.ofDays(7));
        service = new RefreshTokenService(tokens, props, Clock.fixed(NOW, ZoneOffset.UTC));
        user = TestUsers.user("jane@example.com", Roles.AGENT);
    }

    private RefreshToken stored(String raw, Instant expiresAt) {
        return new RefreshToken(user, RefreshTokenService.hash(raw), expiresAt, NOW.minusSeconds(60));
    }

    @Nested
    class Issue {

        @Test
        void issueStoresOnlyTheHashWithTheConfiguredLifetime() {
            String raw = service.issue(user);

            ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
            verify(tokens).save(saved.capture());
            assertEquals(RefreshTokenService.hash(raw), saved.getValue().getTokenHash());
            assertNotEquals(raw, saved.getValue().getTokenHash());
            assertEquals(NOW.plus(Duration.ofDays(7)), saved.getValue().getExpiresAt());
            assertEquals(43, raw.length()); // 32 random bytes, base64url without padding
        }

        @Test
        void issuedTokensAreUnique() {
            assertNotEquals(service.issue(user), service.issue(user));
        }

    }

    @Nested
    class Rotate {

        @Test
        void rotateRevokesTheOldTokenAndIssuesANewOne() {
            RefreshToken old = stored("old-raw", NOW.plusSeconds(3600));
            when(tokens.findByTokenHash(RefreshTokenService.hash("old-raw"))).thenReturn(Optional.of(old));

            RefreshTokenService.Issued issued = service.rotate("old-raw");

            assertTrue(old.isRevoked());
            assertEquals(user, issued.user());
            assertNotEquals("old-raw", issued.rawToken());
            verify(tokens).save(any(RefreshToken.class));
        }

        @Test
        void rotateRejectsUnknownToken() {
            when(tokens.findByTokenHash(any())).thenReturn(Optional.empty());

            assertThrows(InvalidTokenException.class, () -> service.rotate("nope"));
            verify(tokens, never()).save(any());
        }

        @Test
        void rotateRejectsAndRevokesExpiredToken() {
            RefreshToken expired = stored("exp-raw", NOW.minusSeconds(1));
            when(tokens.findByTokenHash(RefreshTokenService.hash("exp-raw"))).thenReturn(Optional.of(expired));

            assertThrows(InvalidTokenException.class, () -> service.rotate("exp-raw"));
            assertTrue(expired.isRevoked());
            verify(tokens, never()).save(any());
        }

        @Test
        void reusingARevokedTokenRevokesEverySessionOfThatUser() {
            RefreshToken used = stored("used-raw", NOW.plusSeconds(3600));
            used.revoke(NOW.minusSeconds(30));
            when(tokens.findByTokenHash(RefreshTokenService.hash("used-raw"))).thenReturn(Optional.of(used));

            assertThrows(InvalidTokenException.class, () -> service.rotate("used-raw"));
            verify(tokens).revokeAllForUser(user.getId(), NOW);
            verify(tokens, never()).save(any());
        }

        @Test
        void rotateRejectsTokensOfDisabledUsers() {
            TestUsers.set(user, "enabled", false);
            RefreshToken token = stored("dis-raw", NOW.plusSeconds(3600));
            when(tokens.findByTokenHash(RefreshTokenService.hash("dis-raw"))).thenReturn(Optional.of(token));

            assertThrows(InvalidTokenException.class, () -> service.rotate("dis-raw"));
            assertTrue(token.isRevoked());
        }

    }

    @Nested
    class Revoke {

        @Test
        void revokeMarksTheTokenAndIsSafeToRepeat() {
            RefreshToken token = stored("out-raw", NOW.plusSeconds(3600));
            when(tokens.findByTokenHash(RefreshTokenService.hash("out-raw"))).thenReturn(Optional.of(token));

            service.revoke("out-raw");
            Instant firstRevocation = token.getRevokedAt();
            service.revoke("out-raw");

            assertNotNull(firstRevocation);
            assertEquals(firstRevocation, token.getRevokedAt());
        }

        @Test
        void revokeIgnoresUnknownTokens() {
            when(tokens.findByTokenHash(any())).thenReturn(Optional.empty());

            service.revoke("unknown"); // must not throw
            verify(tokens, never()).save(any());
        }

    }

    @Nested
    class TransactionBoundaries {

        // Reuse detection revokes sessions and then rejects the request; a rollback would undo the revocation.
        @Test
        void rotationKeepsItsRevocationsWhenTheRequestIsRejected() throws Exception {
            var rotate = RefreshTokenService.class.getMethod("rotate", String.class);
            var refresh = AuthService.class.getMethod("refresh", String.class);

            for (var method : new java.lang.reflect.Method[] {rotate, refresh}) {
                Transactional tx = method.getAnnotation(Transactional.class);
                assertNotNull(tx, method.toString());
                assertEquals(InvalidTokenException.class, tx.noRollbackFor()[0]);
            }
        }

    }
}
