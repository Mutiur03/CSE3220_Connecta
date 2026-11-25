package com.connecta.backend.inbox.domain;

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
 * One message in a conversation. An inbound message names the customer who sent it, an outbound one the
 * agent (user) who sent it. The Meta message id is unique per organization, so a duplicate webhook event
 * cannot create a second row.
 */
@Entity
@Table(name = "messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Message {

    public static final int EXTERNAL_MESSAGE_ID_MAX_LENGTH = 255;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false, updatable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false, updatable = false)
    private Conversation conversation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private MessageDirection direction;

    /** Set for inbound messages. Null once the customer's participant row is removed. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_participant_id", updatable = false)
    private Participant senderParticipant;

    /** Set for outbound messages. Null once the agent's user is deleted. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sent_by_user_id")
    private User sentByUser;

    @Column(name = "external_message_id", length = EXTERNAL_MESSAGE_ID_MAX_LENGTH, updatable = false)
    private String externalMessageId;

    /** The only column a history reconcile may change, through {@link #correctBody}. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "sent_at", nullable = false, updatable = false)
    private Instant sentAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    private Message(Conversation conversation, MessageDirection direction, Participant senderParticipant,
            User sentByUser, String externalMessageId, String body, Instant sentAt) {
        this.organization = conversation.getOrganization();
        this.conversation = conversation;
        this.direction = direction;
        this.senderParticipant = senderParticipant;
        this.sentByUser = sentByUser;
        this.externalMessageId = externalMessageId;
        this.body = body;
        this.sentAt = sentAt;
    }

    public static Message inbound(Conversation conversation, Participant sender, String externalMessageId,
            String body, Instant sentAt) {
        return new Message(conversation, MessageDirection.INBOUND, sender, null, externalMessageId, body, sentAt);
    }

    public static Message outbound(Conversation conversation, User sentBy, String externalMessageId,
            String body, Instant sentAt) {
        return new Message(conversation, MessageDirection.OUTBOUND, null, sentBy, externalMessageId, body, sentAt);
    }

    /**
     * Names the agent on an outbound message the webhook echo stored before the send finished.
     *
     * @return true when the sender changed, false when the message already had one or is inbound
     */
    public boolean attributeTo(User agent) {
        if (direction == MessageDirection.OUTBOUND && sentByUser == null) {
            this.sentByUser = agent;
            return true;
        }
        return false;
    }

    /**
     * Replaces the body with the text Meta now returns for this message (for example the label {@code [Attachment]}
     * becomes {@code [Photo]} once the media is known). Nothing else about the message changes.
     *
     * @return true when the body changed, false when the text is null or already equal
     */
    public boolean correctBody(String providerText) {
        if (providerText == null || providerText.equals(body)) {
            return false;
        }
        this.body = providerText;
        return true;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof Message other && getId() != null && getId().equals(other.getId());
    }

    @Override
    public int hashCode() {
        return Message.class.hashCode();
    }
}
