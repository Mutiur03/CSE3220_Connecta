package com.connecta.backend.auth.repository;

import com.connecta.backend.auth.domain.RefreshToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** True when the session has another token issued at or after this one: it was rotated away. */
    boolean existsBySessionIdAndCreatedAtGreaterThanEqualAndIdNot(UUID sessionId, Instant createdAt, UUID id);

    /**
     * Compare-and-set on one token: revokes it only if nobody has yet. Returns 1 for the single caller that
     * wins a rotation race and 0 for every other concurrent caller, so a token is never rotated twice.
     */
    @Modifying
    @Query("update RefreshToken t set t.revokedAt = :now where t.id = :id and t.revokedAt is null")
    int claim(@Param("id") UUID id, @Param("now") Instant now);

    @Modifying(clearAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now where t.user.id = :userId and t.revokedAt is null")
    int revokeAllForUser(@Param("userId") UUID userId, @Param("now") Instant now);

    Page<RefreshToken> findByUserIdAndRevokedAtIsNullAndExpiresAtAfter(UUID userId, Instant now, Pageable pageable);

    @Modifying(clearAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now"
            + " where t.user.id = :userId and t.sessionId = :sessionId and t.revokedAt is null and t.expiresAt > :now")
    int revokeSession(@Param("userId") UUID userId, @Param("sessionId") UUID sessionId, @Param("now") Instant now);

    @Modifying(clearAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now"
            + " where t.user.id = :userId and t.sessionId <> :sessionId and t.revokedAt is null")
    int revokeOtherSessions(@Param("userId") UUID userId, @Param("sessionId") UUID sessionId, @Param("now") Instant now);

    /** Stamps the session's live token as used, unless it was already stamped after {@code staleBefore}. */
    @Modifying(clearAutomatically = true)
    @Query("update RefreshToken t set t.lastUsedAt = :now"
            + " where t.user.id = :userId and t.sessionId = :sessionId and t.revokedAt is null"
            + " and t.lastUsedAt < :staleBefore")
    int touchSession(@Param("userId") UUID userId, @Param("sessionId") UUID sessionId,
            @Param("now") Instant now, @Param("staleBefore") Instant staleBefore);

    @Modifying
    int deleteByExpiresAtBefore(Instant cutoff);
}
