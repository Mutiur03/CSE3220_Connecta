package com.connecta.backend.setup.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.connecta.backend.auth.dto.RegisterRequest;
import com.connecta.backend.auth.dto.UserResponse;
import com.connecta.backend.auth.service.EmailAlreadyUsedException;
import com.connecta.backend.setup.repository.SystemSetupRepository;
import com.connecta.backend.support.TestClock;
import com.connecta.backend.support.TestUsers;
import com.connecta.backend.user.domain.Roles;
import com.connecta.backend.user.domain.User;
import com.connecta.backend.user.service.UserService;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class SetupServiceTests {

    private static final RegisterRequest REQUEST = new RegisterRequest(" Boss@Example.com ", "correct-password", "Boss");

    @Mock
    UserService users;

    @Mock
    SystemSetupRepository setup;

    PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    SetupService service;

    @BeforeEach
    void setUp() {
        service = new SetupService(users, setup, encoder, TestClock.FIXED);
    }

    private User ownerCreated() {
        User owner = TestUsers.user("boss@example.com", Roles.OWNER);
        when(users.create(eq(Roles.OWNER), anyString(), anyString(), anyString())).thenReturn(owner);
        return owner;
    }

    @Nested
    class Status {

        @Test
        void setupIsRequiredUntilAnOwnerExists() {
            when(users.ownerExists()).thenReturn(false);

            assertTrue(service.status().setupRequired());
        }

        @Test
        void setupIsNotRequiredOnceAnOwnerExists() {
            when(users.ownerExists()).thenReturn(true);

            assertFalse(service.status().setupRequired());
        }
    }

    @Nested
    class CompleteSetup {

        @Test
        void createsTheOwnerWithAHashedPasswordAndClaimsTheSetupRow() {
            when(users.ownerExists()).thenReturn(false);
            when(users.emailExists("boss@example.com")).thenReturn(false);
            User owner = ownerCreated();
            when(setup.claim(owner.getId(), TestClock.NOW)).thenReturn(1);

            UserResponse response = service.completeSetup(REQUEST);

            assertEquals(Set.of(Roles.OWNER), response.roles());
            ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
            verify(users).create(eq(Roles.OWNER), eq("boss@example.com"), hash.capture(), eq("Boss"));
            assertTrue(encoder.matches("correct-password", hash.getValue()));
        }

        @Test
        void isRefusedOnceAnOwnerExists() {
            when(users.ownerExists()).thenReturn(true);

            assertThrows(SetupAlreadyCompleteException.class, () -> service.completeSetup(REQUEST));
            verify(users, never()).create(any(), any(), any(), any());
        }

        @Test
        void anExistingEmailIsRefused() {
            when(users.ownerExists()).thenReturn(false);
            when(users.emailExists("boss@example.com")).thenReturn(true);

            assertThrows(EmailAlreadyUsedException.class, () -> service.completeSetup(REQUEST));
            verify(users, never()).create(any(), any(), any(), any());
        }

        @Test
        void losingTheRaceForTheSetupRowIsRefused() {
            when(users.ownerExists()).thenReturn(false);
            when(users.emailExists(anyString())).thenReturn(false);
            User owner = ownerCreated();
            when(setup.claim(owner.getId(), TestClock.NOW)).thenReturn(0);

            assertThrows(SetupAlreadyCompleteException.class, () -> service.completeSetup(REQUEST));
        }

        @Test
        void aConcurrentDuplicateEmailIsReportedAsTaken() {
            when(users.ownerExists()).thenReturn(false);
            when(users.emailExists(anyString())).thenReturn(false);
            when(users.create(anyString(), anyString(), anyString(), anyString()))
                    .thenThrow(new DataIntegrityViolationException("dup"));

            assertThrows(EmailAlreadyUsedException.class, () -> service.completeSetup(REQUEST));
            verify(setup, never()).claim(any(UUID.class), any());
        }
    }
}
