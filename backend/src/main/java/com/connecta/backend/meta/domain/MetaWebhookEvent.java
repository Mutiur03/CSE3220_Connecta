package com.connecta.backend.meta.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One verified Meta webhook delivery, stored as received until it is processed. */
@Entity
@Table(name = "meta_webhook_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MetaWebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private WebhookObject object;

    @Column(nullable = false, columnDefinition = "TEXT", updatable = false)
    private String payload;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    public MetaWebhookEvent(WebhookObject object, String payload, Instant receivedAt) {
        this.object = object;
        this.payload = payload;
        this.receivedAt = receivedAt;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof MetaWebhookEvent other && getId() != null && getId().equals(other.getId());
    }

    @Override
    public int hashCode() {
        return MetaWebhookEvent.class.hashCode();
    }
}
