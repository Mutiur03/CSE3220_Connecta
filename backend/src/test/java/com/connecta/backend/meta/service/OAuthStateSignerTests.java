package com.connecta.backend.meta.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.connecta.backend.config.MetaProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OAuthStateSignerTests {

    private static final Instant NOW = Instant.parse("2026-11-22T12:00:00Z");
    private static final UUID ORG = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final OAuthStateSigner signer = new OAuthStateSigner(props("app-secret"));

    @Nested
    class Verify {

        @Test
        void aFreshStateNamesTheOrganizationAndAdminItWasIssuedFor() {
            String state = signer.issue(ORG, USER, NOW);

            assertEquals(new OAuthStateSigner.OAuthState(ORG, USER), signer.verify(state, NOW));
        }

        @Test
        void aStateAtItsExpiryIsRejected() {
            String state = signer.issue(ORG, USER, NOW);

            assertThrows(InvalidOAuthStateException.class,
                    () -> signer.verify(state, NOW.plus(OAuthStateSigner.STATE_TTL)));
        }

        @Test
        void aStateSignedWithAnotherAppSecretIsRejected() {
            String forged = new OAuthStateSigner(props("another-secret")).issue(ORG, USER, NOW);

            assertThrows(InvalidOAuthStateException.class, () -> signer.verify(forged, NOW));
        }

        @Test
        void aPayloadChangedToAnotherOrganizationIsRejected() {
            String state = signer.issue(ORG, USER, NOW);
            String signature = state.substring(state.indexOf('.') + 1);
            String otherPayload = signer.issue(UUID.randomUUID(), USER, NOW).split("\\.")[0];

            assertThrows(InvalidOAuthStateException.class,
                    () -> signer.verify(otherPayload + "." + signature, NOW));
        }

        @Test
        void missingOrMalformedStatesAreRejected() {
            for (String malformed : new String[] {null, "", "no-dot", "a.b.c", "!!.!!"}) {
                assertThrows(InvalidOAuthStateException.class, () -> signer.verify(malformed, NOW),
                        "accepted: " + malformed);
            }
        }
    }

    @Nested
    class Issue {

        @Test
        void expiresAfterTheTimeToLive() {
            assertEquals(Duration.ofMinutes(10), OAuthStateSigner.STATE_TTL);
        }

        @Test
        void withoutAnAppSecretMetaIsNotConfigured() {
            OAuthStateSigner unconfigured = new OAuthStateSigner(props(""));

            assertThrows(MetaNotConfiguredException.class, () -> unconfigured.issue(ORG, USER, NOW));
        }
    }

    private static MetaProperties props(String appSecret) {
        return new MetaProperties("app", appSecret, null, "config", "https://api.example.com", "unused");
    }

    @Nested
    class OrganizationOf {

        @Test
        void readsTheOrganizationANamedStateNamesWithoutCheckingItsSignature() {
            UUID org = UUID.randomUUID();
            String state = new OAuthStateSigner(props("another-secret")).issue(org, USER, NOW);

            assertEquals(java.util.Optional.of(org), signer.organizationIdOf(state));
        }

        @Test
        void aMalformedStateNamesNoOrganization() {
            assertEquals(java.util.Optional.empty(), signer.organizationIdOf(null));
            assertEquals(java.util.Optional.empty(), signer.organizationIdOf("forged.state"));
        }
    }
}
