package com.connecta.backend.user.repository;

import com.connecta.backend.user.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    /** Email must already be normalized (see {@link User#normalizeEmail}). Loads roles eagerly. */
    @EntityGraph(attributePaths = "roles")
    Optional<User> findByEmail(String email);

    Page<User> findAllBy(Pageable pageable);

    boolean existsByEmail(String email);

    boolean existsByRolesName(String roleName);
}
