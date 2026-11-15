package com.connecta.backend.org.domain;

import com.connecta.backend.user.domain.Role;
import com.connecta.backend.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** An invitation to join an organization with a role. Only the token's SHA-256 hash is stored. */
@Entity
@Table(name = "invitations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Invitation {

    public static final int EMAIL_MAX_LENGTH = 254;

    public enum Status {
        PENDING, ACCEPTED, REVOKED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(nullable = false, length = EMAIL_MAX_LENGTH)
    private String email;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invited_by", nullable = false)
    private User invitedBy;

    // CHAR(64) in the migration, as in RefreshToken: a SHA-256 hex digest is always 64 characters.
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    public Invitation(Organization organization, String email, Role role, User invitedBy, String tokenHash,
            Instant createdAt, Instant expiresAt) {
        this.organization = organization;
        this.email = User.normalizeEmail(email);
        this.role = role;
        this.invitedBy = invitedBy;
        this.tokenHash = tokenHash;
        this.status = Status.PENDING;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    /** Only a pending, unexpired invitation can be accepted. */
    public void accept(Clock clock) {
        if (status == Status.ACCEPTED) {
            throw new InvitationAlreadyAcceptedException();
        }
        Instant now = clock.instant();
        if (status == Status.REVOKED || isExpired(now)) {
            throw new InvitationNotAcceptableException();
        }
        status = Status.ACCEPTED;
        acceptedAt = now;
    }

    /** Revoking twice is harmless; an accepted invitation cannot be revoked. */
    public void revoke() {
        if (status == Status.ACCEPTED) {
            throw new InvitationAlreadyAcceptedException();
        }
        status = Status.REVOKED;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof Invitation other && getId() != null && getId().equals(other.getId());
    }

    @Override
    public int hashCode() {
        return Invitation.class.hashCode();
    }
}
