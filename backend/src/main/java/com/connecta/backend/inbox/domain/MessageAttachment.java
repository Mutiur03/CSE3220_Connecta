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
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One image, video, voice note or file sent with a message, stored as the URL it is served from. It belongs to
 * the message's organization. A file name or content type longer than its column is cut (name) or dropped (type).
 */
@Entity
@Table(name = "message_attachments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MessageAttachment {

    public static final int FILE_NAME_MAX_LENGTH = 255;
    public static final int CONTENT_TYPE_MAX_LENGTH = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false, updatable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", nullable = false, updatable = false)
    private Message message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private AttachmentKind kind;

    @Column(nullable = false, columnDefinition = "TEXT", updatable = false)
    private String url;

    @Column(name = "file_name", length = FILE_NAME_MAX_LENGTH, updatable = false)
    private String fileName;

    @Column(name = "content_type", length = CONTENT_TYPE_MAX_LENGTH, updatable = false)
    private String contentType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public MessageAttachment(Message message, AttachmentKind kind, String url, String fileName, String contentType) {
        this.organization = message.getOrganization();
        this.message = message;
        this.kind = Objects.requireNonNull(kind, "kind");
        this.url = Objects.requireNonNull(url, "url");
        this.fileName = fileName == null || fileName.length() <= FILE_NAME_MAX_LENGTH ? fileName
                : fileName.substring(0, FILE_NAME_MAX_LENGTH);
        this.contentType = contentType == null || contentType.length() > CONTENT_TYPE_MAX_LENGTH ? null
                : contentType;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof MessageAttachment other && getId() != null && getId().equals(other.getId());
    }

    @Override
    public int hashCode() {
        return MessageAttachment.class.hashCode();
    }
}
