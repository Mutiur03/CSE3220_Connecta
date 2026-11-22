package com.connecta.backend.product.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LocalProductImageStorageTests {

    private static final UUID ORG_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ORG_B = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID PRODUCT = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final byte[] CONTENT = {9, 8, 7};

    private LocalProductImageStorage storage;

    @BeforeEach
    void setUp() {
        storage = new LocalProductImageStorage();
    }

    @Nested
    class StoreAndRead {

        @Test
        void theStoredBytesReadBackUnderTheSameOrganization() {
            String key = storage.store(ORG_A, PRODUCT, "image/png", CONTENT);

            assertArrayEquals(CONTENT, storage.read(ORG_A, key).orElseThrow());
            assertTrue(key.startsWith("organizations/" + ORG_A + "/"));
        }

        @Test
        void changingTheInputArrayAfterStoringDoesNotChangeTheStoredImage() {
            byte[] input = {1, 2, 3};
            String key = storage.store(ORG_A, PRODUCT, "image/png", input);
            input[0] = 42;

            assertArrayEquals(new byte[] {1, 2, 3}, storage.read(ORG_A, key).orElseThrow());
        }

        @Test
        void anUnknownKeyReadsAsEmpty() {
            assertTrue(storage.read(ORG_A, "organizations/" + ORG_A + "/products/x/missing.png").isEmpty());
            assertTrue(storage.read(ORG_A, null).isEmpty());
        }
    }

    @Nested
    class TenantIsolation {

        @Test
        void anotherOrganizationCannotReadTheKey() {
            String key = storage.store(ORG_A, PRODUCT, "image/png", CONTENT);

            assertFalse(storage.read(ORG_B, key).isPresent());
        }

        @Test
        void aKeyIsOnlyReadableUnderItsOwnPrefix() {
            String key = storage.store(ORG_A, PRODUCT, "image/png", CONTENT);

            assertTrue(storage.read(ORG_A, key).isPresent());
            assertFalse(storage.read(ORG_B, key).isPresent());
        }
    }

    @Nested
    class Delete {

        @Test
        void deletedImagesCannotBeRead() {
            String key = storage.store(ORG_A, PRODUCT, "image/png", CONTENT);

            storage.delete(key);

            assertTrue(storage.read(ORG_A, key).isEmpty());
        }

        @Test
        void deletingAMissingKeyIsNotAnError() {
            assertTrue(storage.read(ORG_A, "organizations/" + ORG_A + "/x.png").isEmpty());
            storage.delete("organizations/" + ORG_A + "/x.png");
        }

        @Test
        void deletingOneKeyLeavesOtherImagesAlone() {
            String kept = storage.store(ORG_A, PRODUCT, "image/png", CONTENT);
            String removed = storage.store(ORG_A, PRODUCT, "image/png", CONTENT);

            storage.delete(removed);

            assertTrue(storage.read(ORG_A, kept).isPresent());
        }
    }

    @Nested
    class PublicUrl {

        @Test
        void isALocalUrlForTheKey() {
            assertEquals("local://organizations/x/y.png", storage.publicUrl("organizations/x/y.png"));
        }

    }
}
