package com.connecta.backend.user.service;

import lombok.RequiredArgsConstructor;
import com.connecta.backend.user.domain.Role;
import com.connecta.backend.user.domain.Roles;
import com.connecta.backend.user.domain.User;
import com.connecta.backend.user.repository.RoleRepository;
import com.connecta.backend.user.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** User accounts. Other features reach users and roles only through this service. */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository users;
    private final RoleRepository roles;

    @Transactional(readOnly = true)
    public boolean emailExists(String normalizedEmail) {
        return users.existsByEmail(normalizedEmail);
    }

    /** Email must already be normalized. Roles are loaded. */
    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String normalizedEmail) {
        return users.findByEmail(normalizedEmail);
    }

    @Transactional(readOnly = true)
    public Optional<User> findEnabledById(UUID id) {
        return users.findById(id).filter(User::isEnabled);
    }

    /** A seeded role by name. Other features get roles from here, never from the role repository. */
    @Transactional(readOnly = true)
    public Role findRole(String name) {
        return roles.findByName(name).orElseThrow(() -> new RoleNotSeededException(name));
    }

    /** True once the system OWNER exists. Before that the first-run setup is the only way in. */
    @Transactional(readOnly = true)
    public boolean ownerExists() {
        return users.existsByRolesName(Roles.OWNER);
    }

    /** Stores an already-encoded password. Checking the old password is the caller's job. */
    @Transactional
    public void changePasswordHash(User user, String newPasswordHash) {
        user.changePasswordHash(newPasswordHash);
        users.saveAndFlush(user);
    }

    /**
     * Stores a new user holding one global role. Flushes so a concurrent duplicate email surfaces here as
     * a DataIntegrityViolationException and not at commit.
     */
    @Transactional
    public User create(String roleName, String normalizedEmail, String passwordHash, String fullName) {
        User user = new User(normalizedEmail, passwordHash, fullName);
        user.addRole(findRole(roleName));
        return users.saveAndFlush(user);
    }
}
