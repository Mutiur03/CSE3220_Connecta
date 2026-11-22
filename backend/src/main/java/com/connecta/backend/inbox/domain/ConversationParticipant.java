package com.connecta.backend.inbox.domain;

import com.connecta.backend.org.domain.Organization;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/** Join row of the many-to-many link between a conversation and a participant. */
@Entity
@Table(name = "conversation_participants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConversationParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false, updatable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false, updatable = false)
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "participant_id", nullable = false, updatable = false)
    private Participant participant;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public ConversationParticipant(Conversation conversation, Participant participant) {
        this.organization = conversation.getOrganization();
        this.conversation = conversation;
        this.participant = participant;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof ConversationParticipant other && getId() != null
                && getId().equals(other.getId());
    }

    @Override
    public int hashCode() {
        return ConversationParticipant.class.hashCode();
    }
}
