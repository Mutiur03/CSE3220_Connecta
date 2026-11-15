package com.connecta.backend.user.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class UserTests {

    @Test
    void emailIsTrimmedAndLowerCased() {
        User user = new User("  Jane.Doe@Example.COM ", "hash", "Jane Doe");

        assertEquals("jane.doe@example.com", user.getEmail());
    }

    @Test
    void normalizeEmailMatchesConstructorBehavior() {
        assertEquals(new User(" A@B.c ", "h", "n").getEmail(), User.normalizeEmail(" A@B.c "));
    }

    @Test
    void fullNameIsTrimmed() {
        assertEquals("Jane Doe", new User("a@b.c", "hash", "  Jane Doe ").getFullName());
    }

    @Test
    void newUserIsEnabledWithoutRoles() {
        User user = new User("a@b.c", "hash", "Jane");

        assertTrue(user.isEnabled());
        assertTrue(user.getRoleNames().isEmpty());
    }

    @Test
    void rolesAreExposedByName() {
        User user = new User("a@b.c", "hash", "Jane");
        user.addRole(new Role(Roles.ADMIN));
        user.addRole(new Role(Roles.AGENT));

        assertEquals(Set.of(Roles.ADMIN, Roles.AGENT), user.getRoleNames());
    }

    @Test
    void equalityIsByIdAndTransientEntitiesAreNeverEqual() {
        UUID id = UUID.randomUUID();
        User a = new User("a@b.c", "hash", "A");
        User b = new User("x@y.z", "hash", "B");
        assertNotEquals(a, b);
        ReflectionTestUtils.setField(a, "id", id);
        ReflectionTestUtils.setField(b, "id", id);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void roleEqualityIsById() {
        UUID id = UUID.randomUUID();
        Role admin = new Role(Roles.ADMIN);
        Role agent = new Role(Roles.AGENT);
        assertNotEquals(admin, agent);
        ReflectionTestUtils.setField(admin, "id", id);
        ReflectionTestUtils.setField(agent, "id", id);

        assertEquals(admin, agent);
        assertEquals(admin.hashCode(), agent.hashCode());
    }
}
