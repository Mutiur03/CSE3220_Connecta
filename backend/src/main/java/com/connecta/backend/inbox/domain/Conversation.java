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

/** One chat thread with customers on one channel, inside one organization. */
@Entity
@Table(name = "conversations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Conversation {

    public static final int EXTERNAL_THREAD_ID_MAX_LENGTH = 255;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false, updatable = false)
    private Organization organization;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private Channel channel;

    @Column(name = "external_thread_id", nullable = false, length = EXTERNAL_THREAD_ID_MAX_LENGTH,
            updatable = false)
    private String externalThreadId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ConversationStatus status = ConversationStatus.OPEN;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Conversation(Organization organization, Channel channel, String externalThreadId) {
        this.organization = organization;
        this.channel = channel;
        this.externalThreadId = externalThreadId;
    }

    /** Moves the last-message time forward only, so a late or retried older message cannot rewind it. */
    public void recordMessageAt(Instant sentAt) {
        if (lastMessageAt == null || sentAt.isAfter(lastMessageAt)) {
            lastMessageAt = sentAt;
        }
    }

    public void changeStatus(ConversationStatus newStatus) {
        this.status = newStatus;
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
        return this == o || o instanceof Conversation other && getId() != null && getId().equals(other.getId());
    }

    @Override
    public int hashCode() {
        return Conversation.class.hashCode();
    }
}
