package com.connecta.backend.inbox.domain;

import com.connecta.backend.org.domain.Organization;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A customer account on one channel, as seen by one organization. */
@Entity
@Table(name = "participants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Participant {

    public static final int DISPLAY_NAME_MAX_LENGTH = 150;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false, updatable = false)
    private Organization organization;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private Channel channel;

    @Column(name = "external_id", nullable = false, length = 255, updatable = false)
    private String externalId;

    @Column(name = "display_name", length = DISPLAY_NAME_MAX_LENGTH)
    private String displayName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Participant(Organization organization, Channel channel, String externalId, String displayName) {
        this.organization = organization;
        this.channel = channel;
        this.externalId = externalId;
        this.displayName = displayName;
    }

    public boolean isNameless() {
        return displayName == null || displayName.isBlank();
    }

    /** Sets the name only when none is stored yet (never overwrites); true when it was set. */
    public boolean nameIfAbsent(String name) {
        if (!isNameless() || name == null || name.isBlank()) {
            return false;
        }
        String trimmed = name.strip();
        displayName = trimmed.length() > DISPLAY_NAME_MAX_LENGTH ? trimmed.substring(0, DISPLAY_NAME_MAX_LENGTH)
                : trimmed;
        return true;
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
        return this == o || o instanceof Participant other && getId() != null && getId().equals(other.getId());
    }

    @Override
    public int hashCode() {
        return Participant.class.hashCode();
    }
}
