package com.connecta.backend.auth.domain;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import com.connecta.backend.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "refresh_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // The migration creates CHAR(64) (a SHA-256 hex digest is always 64 characters); without this
    // Hibernate expects VARCHAR and schema validation refuses to start the app.
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Constant across rotations: identifies the device, while the token row changes on every refresh. */
    @Column(name = "session_id", nullable = false, updatable = false)
    private UUID sessionId;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "ip", length = 45)
    private String ip;

    @Column(name = "last_used_at", nullable = false)
    private Instant lastUsedAt;

    public RefreshToken(User user, String tokenHash, Instant expiresAt, Instant createdAt,
            UUID sessionId, String userAgent, String ip) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
        this.sessionId = sessionId;
        this.userAgent = userAgent;
        this.ip = ip;
        this.lastUsedAt = createdAt;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof RefreshToken other && getId() != null && getId().equals(other.getId());
    }

    @Override
    public int hashCode() {
        return RefreshToken.class.hashCode();
    }
}
