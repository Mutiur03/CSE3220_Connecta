package com.connecta.backend.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.connecta.backend.auth.dto.LoginRequest;
import com.connecta.backend.auth.dto.RegisterRequest;
import com.connecta.backend.auth.dto.UserResponse;
import com.connecta.backend.user.domain.Role;
import com.connecta.backend.user.domain.Roles;
import com.connecta.backend.user.domain.User;
import com.connecta.backend.user.repository.RoleRepository;
import com.connecta.backend.user.repository.UserRepository;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTests {

    @Mock
    UserRepository users;

    @Mock
    RoleRepository roles;

    PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(users, roles, encoder);
    }

    private User existingUser(boolean enabled) throws Exception {
        User user = new User("jane@example.com", encoder.encode("correct-password"), "Jane");
        if (!enabled) {
            var field = User.class.getDeclaredField("enabled");
            field.setAccessible(true);
            field.set(user, false);
        }
        return user;
    }

    @Test
    void registerHashesPasswordNormalizesEmailAndAssignsAgentRole() {
        when(users.existsByEmail("jane@example.com")).thenReturn(false);
        when(roles.findByName(Roles.AGENT)).thenReturn(Optional.of(new Role(Roles.AGENT)));
        when(users.saveAndFlush(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserResponse response = service.register(
                new RegisterRequest("  Jane@Example.com ", "correct-password", "Jane"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(saved.capture());
        assertEquals("jane@example.com", saved.getValue().getEmail());
        assertNotEquals("correct-password", saved.getValue().getPasswordHash());
        assertTrue(encoder.matches("correct-password", saved.getValue().getPasswordHash()));
        assertEquals(Set.of(Roles.AGENT), response.roles());
    }

    @Test
    void registerRejectsExistingEmail() {
        when(users.existsByEmail("jane@example.com")).thenReturn(true);

        assertThrows(EmailAlreadyUsedException.class,
                () -> service.register(new RegisterRequest("Jane@example.com", "correct-password", "Jane")));
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void registerMapsConcurrentDuplicateToEmailAlreadyUsed() {
        when(users.existsByEmail(anyString())).thenReturn(false);
        when(roles.findByName(Roles.AGENT)).thenReturn(Optional.of(new Role(Roles.AGENT)));
        when(users.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("dup"));

        assertThrows(EmailAlreadyUsedException.class,
                () -> service.register(new RegisterRequest("jane@example.com", "correct-password", "Jane")));
    }

    @Test
    void loginSucceedsWithCorrectPasswordAndAnyEmailCase() throws Exception {
        when(users.findByEmail("jane@example.com")).thenReturn(Optional.of(existingUser(true)));

        UserResponse response = service.login(new LoginRequest("JANE@example.com", "correct-password"));

        assertEquals("jane@example.com", response.email());
    }

    @Test
    void loginFailsWithWrongPassword() throws Exception {
        when(users.findByEmail("jane@example.com")).thenReturn(Optional.of(existingUser(true)));

        assertThrows(InvalidCredentialsException.class,
                () -> service.login(new LoginRequest("jane@example.com", "wrong-password")));
    }

    @Test
    void loginFailsForUnknownEmailWithSameException() {
        when(users.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class,
                () -> service.login(new LoginRequest("nobody@example.com", "whatever-password")));
    }

    @Test
    void loginFailsForDisabledUserEvenWithCorrectPassword() throws Exception {
        when(users.findByEmail("jane@example.com")).thenReturn(Optional.of(existingUser(false)));

        assertThrows(InvalidCredentialsException.class,
                () -> service.login(new LoginRequest("jane@example.com", "correct-password")));
    }
}
