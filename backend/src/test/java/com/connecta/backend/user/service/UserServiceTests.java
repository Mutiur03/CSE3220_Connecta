package com.connecta.backend.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.connecta.backend.user.domain.Role;
import com.connecta.backend.user.domain.Roles;
import com.connecta.backend.user.domain.User;
import com.connecta.backend.user.repository.RoleRepository;
import com.connecta.backend.user.repository.UserRepository;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class UserServiceTests {

    private static final String EMAIL = "jane@example.com";

    @Mock
    UserRepository users;

    @Mock
    RoleRepository roles;

    @InjectMocks
    UserService service;

    @Nested
    class EmailExists {

        @Test
        void isTrueWhenTheRepositoryHasTheEmail() {
            when(users.existsByEmail(EMAIL)).thenReturn(true);

            assertTrue(service.emailExists(EMAIL));
        }

        @Test
        void isFalseForAnUnknownEmail() {
            assertFalse(service.emailExists("nobody@example.com"));
        }
    }

    @Nested
    class FindByEmail {

        @Test
        void returnsTheUser() {
            User user = new User(EMAIL, "hash", "Jane");
            when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user));

            assertEquals(Optional.of(user), service.findByEmail(EMAIL));
        }

        @Test
        void isEmptyForAnUnknownEmail() {
            when(users.findByEmail(EMAIL)).thenReturn(Optional.empty());

            assertTrue(service.findByEmail(EMAIL).isEmpty());
        }
    }

    @Nested
    class FindEnabledById {

        private final UUID id = UUID.randomUUID();

        @Test
        void returnsAnEnabledUser() {
            User user = new User(EMAIL, "hash", "Jane");
            when(users.findById(id)).thenReturn(Optional.of(user));

            assertEquals(Optional.of(user), service.findEnabledById(id));
        }

        @Test
        void isEmptyForADisabledUser() throws Exception {
            User user = new User(EMAIL, "hash", "Jane");
            var field = User.class.getDeclaredField("enabled");
            field.setAccessible(true);
            field.set(user, false);
            when(users.findById(id)).thenReturn(Optional.of(user));

            assertTrue(service.findEnabledById(id).isEmpty());
        }

        @Test
        void isEmptyForAnUnknownId() {
            when(users.findById(id)).thenReturn(Optional.empty());

            assertTrue(service.findEnabledById(id).isEmpty());
        }
    }

    @Nested
    class Create {

        @Test
        void savesTheUserWithTheGivenRole() {
            when(roles.findByName(Roles.ADMIN)).thenReturn(Optional.of(new Role(Roles.ADMIN)));
            when(users.saveAndFlush(any(User.class))).thenAnswer(i -> i.getArgument(0));

            User created = service.create(Roles.ADMIN, EMAIL, "hash", "Jane");

            assertEquals(EMAIL, created.getEmail());
            assertEquals("hash", created.getPasswordHash());
            assertEquals(Set.of(Roles.ADMIN), created.getRoleNames());
        }

        @Test
        void failsWhenTheRoleIsNotSeeded() {
            when(roles.findByName(Roles.AGENT)).thenReturn(Optional.empty());

            assertThrows(RoleNotSeededException.class, () -> service.create(Roles.AGENT, EMAIL, "hash", "Jane"));
            verify(users, never()).saveAndFlush(any());
        }

        @Test
        void propagatesAConcurrentDuplicate() {
            when(roles.findByName(Roles.AGENT)).thenReturn(Optional.of(new Role(Roles.AGENT)));
            when(users.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("dup"));

            assertThrows(DataIntegrityViolationException.class,
                    () -> service.create(Roles.AGENT, EMAIL, "hash", "Jane"));
        }
    }

    @Nested
    class OwnerExists {

        @Test
        void asksTheRepositoryForTheOwnerRole() {
            when(users.existsByRolesName(Roles.OWNER)).thenReturn(true);

            assertTrue(service.ownerExists());
        }
    }
}
