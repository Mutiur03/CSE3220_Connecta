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
import java.time.Duration;
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
    public static final int PROFILE_PICTURE_URL_MAX_LENGTH = 2048;
    private static final String HTTPS_PREFIX = "https://";

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

    @Column(name = "profile_picture_url", columnDefinition = "text")
    private String profilePictureUrl;

    @Column(name = "profile_picture_fetched_at")
    private Instant profilePictureFetchedAt;

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

    /** True for an https URL of a sane length: the only kind of picture link we store and hand to browsers. */
    public static boolean isStorablePictureUrl(String url) {
        return url != null && url.length() <= PROFILE_PICTURE_URL_MAX_LENGTH
                && url.length() > HTTPS_PREFIX.length()
                && url.regionMatches(true, 0, HTTPS_PREFIX, 0, HTTPS_PREFIX.length());
    }

    /**
     * True when a profile lookup is due: no name, never looked up, or the last lookup is {@code ttl} old or older
     * (Meta picture URLs are signed and expire, and people change pictures).
     */
    public boolean needsProfileRefresh(Instant now, Duration ttl) {
        return isNameless() || profilePictureFetchedAt == null
                || !profilePictureFetchedAt.plus(ttl).isAfter(now);
    }

    /**
     * Records a successful lookup: the name only if none is stored, the picture replaced when a valid new one came
     * (an old one is kept when Meta sent none), and the lookup time. True when the name or picture changed.
     */
    public boolean applyProfile(String name, String pictureUrl, Instant now) {
        boolean changed = nameIfAbsent(name);
        if (isStorablePictureUrl(pictureUrl) && !pictureUrl.equals(profilePictureUrl)) {
            profilePictureUrl = pictureUrl;
            changed = true;
        }
        profilePictureFetchedAt = now;
        return changed;
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
