package com.connecta.backend.inbox.domain;

import com.connecta.backend.meta.domain.MetaConnectedAccount;
import com.connecta.backend.meta.domain.MetaConnectedPage;
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

    /** The Facebook Page this Messenger conversation belongs to. Null on older rows or once the Page is removed. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meta_page_id")
    private MetaConnectedPage metaPage;

    /** The Instagram account this conversation belongs to. Null on older rows or once the account is removed. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instagram_account_id")
    private MetaConnectedAccount instagramAccount;

    /** The member handling this conversation. Null when unassigned or once that member leaves. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_membership_id")
    private Membership assignee;

    /** A customer wrote since a member last read the conversation. Shared by the whole team. */
    @Column(nullable = false)
    private boolean unread;

    /** When the conversation was last closed. Null while open, and on rows closed before it was recorded. */
    @Column(name = "closed_at")
    private Instant closedAt;

    /** Who last closed the conversation. Null while open or once that user is deleted. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "closed_by_user_id")
    private User closedBy;

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

    public void linkToPage(MetaConnectedPage page) {
        this.metaPage = page;
    }

    public void linkToInstagramAccount(MetaConnectedAccount account) {
        this.instagramAccount = account;
    }

    /** Moves the last-message time forward only, so a late or retried older message cannot rewind it. */
    public void recordMessageAt(Instant sentAt) {
        if (lastMessageAt == null || sentAt.isAfter(lastMessageAt)) {
            lastMessageAt = sentAt;
        }
    }

    /**
     * Hands the conversation to {@code newAssignee} (null unassigns) and returns the history row the
     * caller must save, so the current assignee and the history cannot disagree.
     */
    public ConversationAssignment assignTo(Membership newAssignee, User assignedBy, AssignmentSource source) {
        ConversationAssignment record = new ConversationAssignment(this, assignee, newAssignee, assignedBy, source);
        this.assignee = newAssignee;
        return record;
    }

    /**
     * Moves to {@code newStatus}. Closing an open conversation records when and by whom; closing it again
     * keeps the first close. Reopening clears both, so a reopened conversation does not count as resolved.
     */
    public void changeStatus(ConversationStatus newStatus, User changedBy, Instant changedAt) {
        if (newStatus == ConversationStatus.CLOSED && status != ConversationStatus.CLOSED) {
            closedAt = changedAt;
            closedBy = changedBy;
        } else if (newStatus != ConversationStatus.CLOSED) {
            closedAt = null;
            closedBy = null;
        }
        this.status = newStatus;
    }

    public void markUnread() {
        this.unread = true;
    }

    public void markRead() {
        this.unread = false;
    }

    public void changeReadState(boolean unread) {
        this.unread = unread;
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
