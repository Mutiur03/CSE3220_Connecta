package com.connecta.backend.org.domain;

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

/** One stretch of time a member was AVAILABLE. Open (no end) while they still are. */
@Entity
@Table(name = "availability_intervals")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AvailabilityInterval {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false, updatable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "membership_id", nullable = false, updatable = false)
    private Membership membership;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    /** Last moment the member was known to be connected; where a restart closes a still-open interval. */
    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    public AvailabilityInterval(Membership membership, Instant startedAt) {
        this.organization = membership.getOrganization();
        this.membership = membership;
        this.startedAt = startedAt;
        this.lastSeenAt = startedAt;
    }

    /** Records a sighting; an earlier time than the one already known is ignored. */
    public void touch(Instant seenAt) {
        if (lastSeenAt == null || seenAt.isAfter(lastSeenAt)) {
            lastSeenAt = seenAt;
        }
    }

    public void close(Instant endedAt) {
        if (endedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("An interval cannot end before it starts");
        }
        this.endedAt = endedAt;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof AvailabilityInterval other && getId() != null && getId().equals(other.getId());
    }

    @Override
    public int hashCode() {
        return AvailabilityInterval.class.hashCode();
    }
}
