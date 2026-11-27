package com.connecta.backend.org.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Whether an admin lets one member take automatic assignments, and when they last got one. A member
 * without this row is not assignable.
 */
@Entity
@Table(name = "agent_availability")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgentAvailability {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false, updatable = false)
    private Organization organization;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "membership_id", nullable = false, updatable = false, unique = true)
    private Membership membership;

    /** Admin-controlled: only assignable members get automatic assignments, online or not. */
    @Column(nullable = false)
    private boolean assignable;

    /** Round-robin cursor: the next automatic assignment goes to the member who waited longest. */
    @Column(name = "last_assigned_at")
    private Instant lastAssignedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public AgentAvailability(Membership membership) {
        this.organization = membership.getOrganization();
        this.membership = membership;
    }

    public void changeAssignable(boolean assignable) {
        this.assignable = assignable;
    }

    public void recordAssignmentAt(Instant assignedAt) {
        this.lastAssignedAt = assignedAt;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof AgentAvailability other && getId() != null && getId().equals(other.getId());
    }

    @Override
    public int hashCode() {
        return AgentAvailability.class.hashCode();
    }
}
