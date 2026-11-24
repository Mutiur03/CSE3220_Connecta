package com.connecta.backend.auth.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.connecta.backend.user.domain.User;
import com.connecta.backend.user.service.UserService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class PasswordServiceTests {

    static final String CURRENT = "current-password";
    static final String NEW = "brand-new-password";
    static final UUID SESSION = UUID.randomUUID();

    @Mock
    UserService users;

    @Mock
    SessionService sessions;

    final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    PasswordService service;
    User user;

    @BeforeEach
    void setUp() {
        service = new PasswordService(users, encoder, sessions);
        user = new User("jane@example.com", encoder.encode(CURRENT), "Jane");
    }

    private UUID userId() {
        return UUID.randomUUID();
    }

    @Nested
    class Change {

        @Test
        void storesTheNewHashAndSignsOutTheOtherDevices() {
            UUID id = userId();
            when(users.findEnabledById(id)).thenReturn(Optional.of(user));

            service.change(id, SESSION, CURRENT, NEW);

            ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
            verify(users).changePasswordHash(eq(user), hash.capture());
            assertTrue(encoder.matches(NEW, hash.getValue()));
            verify(sessions).revokeOthers(id, SESSION);
            verify(sessions, never()).revokeAll(any());
        }

        @Test
        void signsEverythingOutWhenTheCurrentDeviceIsUnknown() {
            UUID id = userId();
            when(users.findEnabledById(id)).thenReturn(Optional.of(user));

            service.change(id, null, CURRENT, NEW);

            verify(sessions).revokeAll(id);
            verify(sessions, never()).revokeOthers(any(), any());
        }

        @Test
        void rejectsAWrongCurrentPasswordAndChangesNothing() {
            UUID id = userId();
            when(users.findEnabledById(id)).thenReturn(Optional.of(user));

            assertThrows(WrongPasswordException.class, () -> service.change(id, SESSION, "not-it-at-all", NEW));

            verify(users, never()).changePasswordHash(any(), any());
            verify(sessions, never()).revokeOthers(any(), any());
        }

        @Test
        void rejectsANewPasswordThatEqualsTheCurrentOne() {
            UUID id = userId();
            when(users.findEnabledById(id)).thenReturn(Optional.of(user));

            assertThrows(PasswordUnchangedException.class, () -> service.change(id, SESSION, CURRENT, CURRENT));

            verify(users, never()).changePasswordHash(any(), any());
            verify(sessions, never()).revokeOthers(any(), any());
        }

        @Test
        void rejectsAnAccountThatIsGoneOrDisabled() {
            UUID id = userId();
            when(users.findEnabledById(id)).thenReturn(Optional.empty());

            assertThrows(AccountNotAvailableException.class, () -> service.change(id, SESSION, CURRENT, NEW));

            verify(users, never()).changePasswordHash(any(), any());
        }
    }
}
