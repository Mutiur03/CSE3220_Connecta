package com.connecta.backend.meta.domain;

import com.connecta.backend.org.domain.Organization;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** The Meta user grant of one organization. The access token is stored only encrypted. */
@Entity
@Table(name = "meta_authorizations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MetaAuthorization {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false, updatable = false)
    private Organization organization;

    /** The admin who connected Meta. Null once that user is deleted. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "granted_by_user_id")
    private User grantedBy;

    @Column(name = "access_token_encrypted", nullable = false, columnDefinition = "TEXT")
    private String accessTokenEncrypted;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public MetaAuthorization(Organization organization) {
        this.organization = organization;
    }

    /** Stores a newly authorized token, replacing the previous one. The caller passes the encrypted value. */
    public void replaceToken(String accessTokenEncrypted, User grantedBy, Instant expiresAt, Instant now) {
        this.accessTokenEncrypted = accessTokenEncrypted;
        this.grantedBy = grantedBy;
        this.expiresAt = expiresAt;
        this.updatedAt = now;
        if (createdAt == null) {
            this.createdAt = now;
        }
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof MetaAuthorization other && getId() != null && getId().equals(other.getId());
    }

    @Override
    public int hashCode() {
        return MetaAuthorization.class.hashCode();
    }
}
