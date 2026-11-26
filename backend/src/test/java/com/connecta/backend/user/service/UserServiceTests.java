package com.connecta.backend.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.connecta.backend.support.TestUsers;
import com.connecta.backend.user.domain.Role;
import com.connecta.backend.user.domain.Roles;
import com.connecta.backend.user.domain.User;
import com.connecta.backend.user.repository.RoleRepository;
import com.connecta.backend.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import com.connecta.backend.common.dto.PageResponse;
import com.connecta.backend.user.dto.AdminUserResponse;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class UserServiceTests {

    private static final String EMAIL = "jane@example.com";

    @Mock
    UserRepository users;

    @Mock
    RoleRepository roles;

    @Mock
    ApplicationEventPublisher events;

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

    @Nested
    class Disable {

        @Test
        void disablesTheUserAndPublishesTheEventOnce() {
            User user = TestUsers.user(EMAIL, Roles.AGENT);
            when(users.findById(user.getId())).thenReturn(Optional.of(user));

            service.disable(user.getId());

            assertFalse(user.isEnabled());
            verify(events, times(1)).publishEvent(new UserDisabledEvent(user.getId()));
        }

        @Test
        void anAlreadyDisabledUserPublishesNothing() {
            User user = TestUsers.user(EMAIL, Roles.AGENT);
            user.disable();
            when(users.findById(user.getId())).thenReturn(Optional.of(user));

            service.disable(user.getId());

            verify(events, never()).publishEvent(any(Object.class));
        }

        @Test
        void theSystemOwnerCannotBeDisabled() {
            User owner = TestUsers.user(EMAIL, Roles.OWNER);
            when(users.findById(owner.getId())).thenReturn(Optional.of(owner));

            assertThrows(UserProtectedException.class, () -> service.disable(owner.getId()));
            assertTrue(owner.isEnabled());
            verify(events, never()).publishEvent(any(Object.class));
        }

        @Test
        void failsForAnUnknownUserAndPublishesNothing() {
            UUID id = UUID.randomUUID();

            assertThrows(UserNotFoundException.class, () -> service.disable(id));
            verify(events, never()).publishEvent(any(Object.class));
        }
    }

    @Nested
    class Enable {

        @Test
        void enablesADisabledUser() {
            User user = TestUsers.user(EMAIL, Roles.AGENT);
            user.disable();
            when(users.findById(user.getId())).thenReturn(Optional.of(user));

            service.enable(user.getId());

            assertTrue(user.isEnabled());
            verify(events, never()).publishEvent(any(Object.class));
        }

        @Test
        void failsForAnUnknownUser() {
            assertThrows(UserNotFoundException.class, () -> service.enable(UUID.randomUUID()));
        }
    }

    @Nested
    class ChangeEnabled {

        @Test
        void falseDisablesAndReturnsTheUpdatedUser() {
            User user = TestUsers.user(EMAIL, Roles.AGENT);
            when(users.findById(user.getId())).thenReturn(Optional.of(user));

            AdminUserResponse response = service.changeEnabled(user.getId(), false);

            assertFalse(response.enabled());
            assertEquals(List.of(Roles.AGENT), response.roles());
        }

        @Test
        void trueEnables() {
            User user = TestUsers.user(EMAIL, Roles.AGENT);
            user.disable();
            when(users.findById(user.getId())).thenReturn(Optional.of(user));

            assertTrue(service.changeEnabled(user.getId(), true).enabled());
        }
    }

    @Nested
    class ListAll {

        @Test
        void mapsOnePageAskingForTheCallerFirst() {
            User user = TestUsers.user(EMAIL, Roles.AGENT);
            UUID callerId = UUID.randomUUID();
            when(users.findAllCallerFirst(eq(callerId), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(user)));

            PageResponse<AdminUserResponse> page = service.listAll(callerId, 0, 20);

            assertEquals(1, page.totalElements());
            assertEquals(EMAIL, page.content().get(0).email());
            ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
            verify(users).findAllCallerFirst(eq(callerId), pageable.capture());
            assertEquals(0, pageable.getValue().getPageNumber());
            assertEquals(20, pageable.getValue().getPageSize());
            assertTrue(pageable.getValue().getSort().isUnsorted());
        }
    }
}
