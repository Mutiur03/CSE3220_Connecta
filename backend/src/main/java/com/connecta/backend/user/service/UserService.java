package com.connecta.backend.user.service;

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
public class UserService {

    private final UserRepository users;
    private final RoleRepository roles;

    public UserService(UserRepository users, RoleRepository roles) {
        this.users = users;
        this.roles = roles;
    }

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

    /**
     * Stores a new user with the AGENT role. Flushes so a concurrent duplicate email surfaces here as
     * a DataIntegrityViolationException and not at commit.
     */
    @Transactional
    public User createAgent(String normalizedEmail, String passwordHash, String fullName) {
        Role agent = roles.findByName(Roles.AGENT)
                .orElseThrow(() -> new IllegalStateException("Role AGENT is not seeded"));
        User user = new User(normalizedEmail, passwordHash, fullName);
        user.addRole(agent);
        return users.saveAndFlush(user);
    }
}
