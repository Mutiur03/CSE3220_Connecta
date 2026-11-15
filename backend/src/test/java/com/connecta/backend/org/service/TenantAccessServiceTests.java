package com.connecta.backend.org.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.connecta.backend.org.domain.Membership;
import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.org.repository.MembershipRepository;
import com.connecta.backend.user.domain.Role;
import com.connecta.backend.user.domain.Roles;
import com.connecta.backend.user.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;

@ExtendWith(MockitoExtension.class)
class TenantAccessServiceTests {

    private static final UUID ORGANIZATION = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();

    @Mock
    MembershipRepository memberships;

    TenantAccessService service;

    @BeforeEach
    void setUp() {
        service = new TenantAccessService(memberships, RoleHierarchyImpl.fromHierarchy(
                "ROLE_OWNER > ROLE_ADMIN\nROLE_ADMIN > ROLE_AGENT"));
    }

    private static Membership membership(String roleName) {
        return new Membership(new Organization("Org", "org"),
                new User("jane@example.com", "hash", "Jane"), new Role(roleName));
    }

    private void memberOf(String roleName) {
        when(memberships.findByOrganizationIdAndUserId(ORGANIZATION, USER))
                .thenReturn(Optional.of(membership(roleName)));
    }

    private void notAMember() {
        when(memberships.findByOrganizationIdAndUserId(ORGANIZATION, USER)).thenReturn(Optional.empty());
    }

    @Nested
    class RequireMembership {

        @Test
        void returnsTheMembershipOfAMember() {
            Membership held = membership(Roles.AGENT);
            when(memberships.findByOrganizationIdAndUserId(ORGANIZATION, USER)).thenReturn(Optional.of(held));

            assertSame(held, service.requireMembership(ORGANIZATION, USER));
        }

        @Test
        void throwsNotAMemberForAUserWithoutMembership() {
            notAMember();

            assertThrows(NotAMemberException.class, () -> service.requireMembership(ORGANIZATION, USER));
        }

        @Test
        void unknownOrganizationGivesTheSameMessageAsNotAMember() {
            notAMember();

            NotAMemberException e = assertThrows(NotAMemberException.class,
                    () -> service.requireMembership(ORGANIZATION, USER));
            assertEquals(new NotAMemberException().getMessage(), e.getMessage());
        }
    }

    @Nested
    class RequireRole {

        @Test
        void allowsTheExactRequiredRole() {
            memberOf(Roles.ADMIN);

            assertEquals(Roles.ADMIN, service.requireRole(ORGANIZATION, USER, Roles.ADMIN).getRole().getName());
        }

        @Test
        void allowsAHigherRole() {
            memberOf(Roles.OWNER);

            assertEquals(Roles.OWNER, service.requireRole(ORGANIZATION, USER, Roles.AGENT).getRole().getName());
        }

        @Test
        void rejectsALowerRoleWithInsufficientRole() {
            memberOf(Roles.AGENT);

            assertThrows(InsufficientOrganizationRoleException.class,
                    () -> service.requireRole(ORGANIZATION, USER, Roles.ADMIN));
        }

        @Test
        void nonMemberGetsNotAMemberNotInsufficientRole() {
            notAMember();

            assertThrows(NotAMemberException.class, () -> service.requireRole(ORGANIZATION, USER, Roles.AGENT));
        }
    }

    @Nested
    class HasAtLeast {

        @ParameterizedTest(name = "{0} at least {1} = {2}")
        @CsvSource({
            "OWNER, OWNER, true",
            "OWNER, ADMIN, true",
            "OWNER, AGENT, true",
            "ADMIN, OWNER, false",
            "ADMIN, ADMIN, true",
            "ADMIN, AGENT, true",
            "AGENT, OWNER, false",
            "AGENT, ADMIN, false",
            "AGENT, AGENT, true"
        })
        void followsTheOwnerAdminAgentHierarchy(String held, String required, boolean expected) {
            assertEquals(expected, service.hasAtLeast(membership(held), required));
        }

        @Test
        void aRoleOutsideTheHierarchyIsNeverEnough() {
            assertFalse(service.hasAtLeast(membership("GUEST"), Roles.AGENT));
        }

        @Test
        void anUnknownRequiredRoleIsNeverGranted() {
            assertFalse(service.hasAtLeast(membership(Roles.OWNER), "GUEST"));
        }
    }
}
