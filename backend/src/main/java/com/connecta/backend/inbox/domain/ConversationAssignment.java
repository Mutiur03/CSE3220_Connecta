package com.connecta.backend.inbox.domain;

import com.connecta.backend.org.domain.Membership;
import com.connecta.backend.org.domain.Organization;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One hand-over of a conversation, kept as history and never changed. A null {@code fromMember} is the
 * first assignment, a null {@code toMember} an unassignment, and a null {@code assignedBy} an automatic one.
 */
@Entity
@Table(name = "conversation_assignments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConversationAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false, updatable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false, updatable = false)
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_membership_id", updatable = false)
    private Membership fromMember;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_membership_id", updatable = false)
    private Membership toMember;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by_user_id", updatable = false)
    private User assignedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32, updatable = false)
    private AssignmentSource source;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    ConversationAssignment(Conversation conversation, Membership fromMember, Membership toMember,
            User assignedBy, AssignmentSource source) {
        this.organization = conversation.getOrganization();
        this.conversation = conversation;
        this.fromMember = fromMember;
        this.toMember = toMember;
        this.assignedBy = assignedBy;
        this.source = source;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof ConversationAssignment other && getId() != null
                && getId().equals(other.getId());
    }

    @Override
    public int hashCode() {
        return ConversationAssignment.class.hashCode();
    }
}
