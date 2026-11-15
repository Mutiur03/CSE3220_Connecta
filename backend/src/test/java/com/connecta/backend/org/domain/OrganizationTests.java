package com.connecta.backend.org.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class OrganizationTests {

    @Test
    void nameIsTrimmedAndSlugIsKeptAsGiven() {
        Organization org = new Organization("  Acme Support ", "acme-support");

        assertEquals("Acme Support", org.getName());
        assertEquals("acme-support", org.getSlug());
    }

    @Test
    void equalityIsByIdAndTransientEntitiesAreNeverEqual() {
        UUID id = UUID.randomUUID();
        Organization a = new Organization("A", "a");
        Organization b = new Organization("B", "b");
        assertNotEquals(a, b);
        ReflectionTestUtils.setField(a, "id", id);
        ReflectionTestUtils.setField(b, "id", id);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
