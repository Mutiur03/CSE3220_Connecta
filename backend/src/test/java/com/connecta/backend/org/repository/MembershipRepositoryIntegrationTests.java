package com.connecta.backend.org.repository;

import com.connecta.backend.support.SharedPostgres;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.connecta.backend.org.domain.Membership;
import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.support.RandomJwtSecret;
import com.connecta.backend.support.TestClock;
import com.connecta.backend.user.domain.Role;
import com.connecta.backend.user.domain.Roles;
import com.connecta.backend.user.domain.User;
import com.connecta.backend.user.repository.RoleRepository;
import com.connecta.backend.user.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ContextConfiguration;

/**
 * Real PostgreSQL: the memberships migration is applied by Flyway, and the many-to-many link
 * between users and organizations is checked end to end. Skipped when Docker is absent.
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false"})
@Import(TestClock.class)
@ContextConfiguration(initializers = RandomJwtSecret.class)
class MembershipRepositoryIntegrationTests extends SharedPostgres {

    @Autowired
    OrganizationRepository organizations;

    @Autowired
    MembershipRepository memberships;

    @Autowired
    UserRepository users;

    @Autowired
    RoleRepository roles;

    private User newUser() {
        return users.saveAndFlush(new User("u" + System.nanoTime() + "@example.com", "hash", "Test User"));
    }

    private Organization newOrganization() {
        return organizations.saveAndFlush(new Organization("Org", "org-" + System.nanoTime()));
    }

    private Role role(String name) {
        return roles.findByName(name).orElseThrow();
    }

    @Test
    void oneUserCanBelongToSeveralOrganizationsWithADifferentRoleInEach() {
        User user = newUser();
        Organization first = newOrganization();
        Organization second = newOrganization();
        memberships.saveAndFlush(new Membership(first, user, role(Roles.ADMIN)));
        memberships.saveAndFlush(new Membership(second, user, role(Roles.AGENT)));

        List<Membership> found = memberships.findAllByUserId(user.getId());

        assertEquals(2, found.size());
        assertTrue(found.stream().anyMatch(m -> m.getOrganization().equals(first)
                && m.getRole().getName().equals(Roles.ADMIN)));
        assertTrue(found.stream().anyMatch(m -> m.getOrganization().equals(second)
                && m.getRole().getName().equals(Roles.AGENT)));
    }

    @Test
    void lookupByOrganizationAndUserReturnsNothingForAnotherOrganization() {
        User user = newUser();
        Organization member = newOrganization();
        Organization outsider = newOrganization();
        memberships.saveAndFlush(new Membership(member, user, role(Roles.ADMIN)));

        assertTrue(memberships.findByOrganizationIdAndUserId(member.getId(), user.getId()).isPresent());
        assertTrue(memberships.findByOrganizationIdAndUserId(outsider.getId(), user.getId()).isEmpty());
    }

    @Test
    void sameUserCannotJoinTheSameOrganizationTwice() {
        User user = newUser();
        Organization org = newOrganization();
        memberships.saveAndFlush(new Membership(org, user, role(Roles.AGENT)));

        assertThrows(DataIntegrityViolationException.class,
                () -> memberships.saveAndFlush(new Membership(org, user, role(Roles.ADMIN))));
    }
}
