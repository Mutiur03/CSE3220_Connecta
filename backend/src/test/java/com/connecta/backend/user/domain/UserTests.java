package com.connecta.backend.user.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

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
        user.addRole(new Role(Roles.ADMIN)); // same name, same role

        assertEquals(Set.of(Roles.ADMIN, Roles.AGENT), user.getRoleNames());
    }
}
