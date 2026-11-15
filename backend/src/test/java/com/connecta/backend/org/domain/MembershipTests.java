package com.connecta.backend.org.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.connecta.backend.support.TestUsers;
import com.connecta.backend.user.domain.Role;
import com.connecta.backend.user.domain.Roles;
import com.connecta.backend.user.domain.User;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MembershipTests {

    @Test
    void keepsTheOrganizationUserAndRoleItWasCreatedWith() {
        Organization org = new Organization("Acme", "acme");
        User user = TestUsers.user("jane@example.com");
        Role role = new Role(Roles.ADMIN);

        Membership membership = new Membership(org, user, role);

        assertSame(org, membership.getOrganization());
        assertSame(user, membership.getUser());
        assertSame(role, membership.getRole());
    }

    @Test
    void equalityIsByIdAndTransientEntitiesAreNeverEqual() {
        UUID id = UUID.randomUUID();
        Membership a = new Membership(new Organization("A", "a"), TestUsers.user("a@example.com"), new Role(Roles.AGENT));
        Membership b = new Membership(new Organization("B", "b"), TestUsers.user("b@example.com"), new Role(Roles.AGENT));
        assertNotEquals(a, b);
        ReflectionTestUtils.setField(a, "id", id);
        ReflectionTestUtils.setField(b, "id", id);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
