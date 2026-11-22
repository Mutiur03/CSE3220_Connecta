package com.connecta.backend.meta.domain;

import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.user.domain.User;
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

/** A Facebook Page connected to an organization for Messenger. The Page token is stored only encrypted. */
@Entity
@Table(name = "meta_connected_pages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MetaConnectedPage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false, updatable = false)
    private Organization organization;

    @Column(name = "page_id", nullable = false, length = 64, updatable = false)
    private String pageId;

    @Column(name = "page_name", nullable = false, length = 255)
    private String pageName;

    @Column(name = "page_access_token_encrypted", nullable = false, columnDefinition = "TEXT")
    private String pageAccessTokenEncrypted;

    /** The admin who connected the Page. Null once that user is deleted. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "granted_by_user_id")
    private User grantedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public MetaConnectedPage(Organization organization, String pageId) {
        this.organization = organization;
        this.pageId = pageId;
    }

    /** Stores the Page's current name and token, replacing whatever was there. The caller passes the encrypted token. */
    public void replace(String pageName, String pageAccessTokenEncrypted, User grantedBy, Instant now) {
        this.pageName = pageName;
        this.pageAccessTokenEncrypted = pageAccessTokenEncrypted;
        this.grantedBy = grantedBy;
        this.updatedAt = now;
        if (createdAt == null) {
            this.createdAt = now;
        }
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof MetaConnectedPage other && getId() != null && getId().equals(other.getId());
    }

    @Override
    public int hashCode() {
        return MetaConnectedPage.class.hashCode();
    }
}
