package com.connecta.backend.org.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class InvitationTokenGeneratorTests {

    /** SHA-256 of "abc", from the FIPS 180-2 test vectors. */
    private static final String ABC_SHA256 = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    private final InvitationTokenGenerator generator = new InvitationTokenGenerator();

    @Nested
    class Hashing {

        @Test
        void isTheLowerCaseHexSha256OfTheToken() {
            assertEquals(ABC_SHA256, generator.hash("abc"));
        }
    }

    @Nested
    class Issuing {

        @Test
        void rawTokenIsUrlSafeAndLongEnoughToGuess() {
            String raw = generator.issue().raw();

            assertEquals(43, raw.length());
            assertTrue(raw.matches("[A-Za-z0-9_-]+"), raw);
        }

        @Test
        void hashIsTheHashOfTheRawTokenAndNeverTheRawToken() {
            InvitationTokenGenerator.Issued issued = generator.issue();

            assertEquals(64, issued.hash().length());
            assertEquals(generator.hash(issued.raw()), issued.hash());
            assertNotEquals(issued.raw(), issued.hash());
        }

        @Test
        void everyIssuedTokenIsDifferent() {
            Set<String> raws = new HashSet<>();
            for (int i = 0; i < 100; i++) {
                assertTrue(raws.add(generator.issue().raw()));
            }
        }
    }
}
