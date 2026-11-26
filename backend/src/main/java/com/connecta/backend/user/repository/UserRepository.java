package com.connecta.backend.user.repository;

import com.connecta.backend.user.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

    /** Email must already be normalized (see {@link User#normalizeEmail}). Loads roles eagerly. */
    @EntityGraph(attributePaths = "roles")
    Optional<User> findByEmail(String email);

    /** Every user, the given caller first, then by email. Pass an unsorted Pageable: the order is in the query. */
    @Query(value = "select u from User u order by case when u.id = :callerId then 0 else 1 end, u.email, u.id",
            countQuery = "select count(u) from User u")
    Page<User> findAllCallerFirst(@Param("callerId") UUID callerId, Pageable pageable);

    boolean existsByEmail(String email);

    boolean existsByRolesName(String roleName);
}
