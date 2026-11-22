package com.connecta.backend.meta.domain;

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
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A Meta message an organization has already received; the record that makes redelivery harmless. */
@Entity
@Table(name = "meta_processed_messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MetaProcessedMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false, updatable = false)
    private Organization organization;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private WebhookObject object;

    @Column(name = "external_message_id", nullable = false, length = 255, updatable = false)
    private String externalMessageId;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    public MetaProcessedMessage(Organization organization, WebhookObject object, String externalMessageId,
            Instant processedAt) {
        this.organization = organization;
        this.object = object;
        this.externalMessageId = externalMessageId;
        this.processedAt = processedAt;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof MetaProcessedMessage other && getId() != null && getId().equals(other.getId());
    }

    @Override
    public int hashCode() {
        return MetaProcessedMessage.class.hashCode();
    }
}
