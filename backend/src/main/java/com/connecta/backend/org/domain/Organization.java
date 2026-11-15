package com.connecta.backend.org.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "organizations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Organization {

    public static final int NAME_MAX_LENGTH = 100;
    public static final int SLUG_MAX_LENGTH = 60;
    /** Lower-case words joined by single hyphens. Mirrors the organizations_slug_format check. */
    public static final String SLUG_PATTERN = "^[a-z0-9]+(-[a-z0-9]+)*$";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = NAME_MAX_LENGTH)
    private String name;

    @Column(nullable = false, unique = true, length = SLUG_MAX_LENGTH)
    private String slug;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Organization(String name, String slug) {
        this.name = name.trim();
        this.slug = slug;
    }

    /** The slug is fixed for the life of the organization; only the display name changes. */
    public void rename(String newName) {
        this.name = newName.trim();
    }

    @PrePersist
    void onCreate() {
        createdAt = updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof Organization other && getId() != null && getId().equals(other.getId());
    }

    @Override
    public int hashCode() {
        return Organization.class.hashCode();
    }
}
