package com.connecta.backend.meta.domain;

import com.connecta.backend.inbox.domain.Conversation;
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
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A Meta account an organization connected (Instagram professional account). The token is stored encrypted. */
@Entity
@Table(name = "meta_connected_accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MetaConnectedAccount implements ConnectedChannelAccount {

    public static final String PLATFORM_INSTAGRAM = "INSTAGRAM";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false, updatable = false)
    private Organization organization;

    @Column(nullable = false, length = 16, updatable = false)
    private String platform;

    @Column(name = "external_account_id", nullable = false, length = 64, updatable = false)
    private String externalAccountId;

    @Column(length = 150)
    private String username;

    @Column(name = "access_token_encrypted", nullable = false, columnDefinition = "TEXT")
    private String accessTokenEncrypted;

    /** The admin who connected the account. Null once that user is deleted. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "granted_by_user_id")
    private User grantedBy;

    /** When the past conversations were last imported to the end of a run; null until one succeeded. */
    @Column(name = "history_imported_at")
    private Instant historyImportedAt;

    /** Why the last import failed; null when none did or a later one succeeded. */
    @Enumerated(EnumType.STRING)
    @Column(name = "history_import_error", length = 16)
    private HistoryImportFailure historyImportError;

    @Column(name = "history_import_failed_at")
    private Instant historyImportFailedAt;

    /** When a webhook last arrived for this connection; null until one does. */
    @Column(name = "last_webhook_at")
    private Instant lastWebhookAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public MetaConnectedAccount(Organization organization, String platform, String externalAccountId) {
        this.organization = organization;
        this.platform = platform;
        this.externalAccountId = externalAccountId;
    }

    /** Stores a (re)connection: the new username, the encrypted token and the admin who connected it. */
    public void refresh(String username, String accessTokenEncrypted, User grantedBy, Instant now) {
        this.username = username;
        this.accessTokenEncrypted = accessTokenEncrypted;
        this.grantedBy = grantedBy;
        this.updatedAt = now;
        if (createdAt == null) {
            this.createdAt = now;
        }
    }

    public void markHistoryImported(Instant at) {
        this.historyImportedAt = at;
        this.historyImportError = null;
        this.historyImportFailedAt = null;
    }

    public void markHistoryImportFailed(HistoryImportFailure failure, Instant at) {
        this.historyImportError = failure;
        this.historyImportFailedAt = at;
    }

    @Override
    public void markWebhookReceived(Instant at) {
        this.lastWebhookAt = at;
    }

    @Override
    public void linkTo(Conversation conversation) {
        conversation.linkToInstagramAccount(this);
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof MetaConnectedAccount other && getId() != null && getId().equals(other.getId());
    }

    @Override
    public int hashCode() {
        return MetaConnectedAccount.class.hashCode();
    }
}
