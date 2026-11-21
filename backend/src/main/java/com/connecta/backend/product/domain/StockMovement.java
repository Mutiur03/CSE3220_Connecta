package com.connecta.backend.product.domain;

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

/** One recorded change to a product's stock: who, when, why, and the level before-and-after. */
@Entity
@Table(name = "stock_movements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockMovement {

    public static final int REASON_MAX_LENGTH = 255;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false, updatable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private StockMovementType type;

    @Column(name = "quantity_delta", nullable = false, updatable = false)
    private int quantityDelta;

    @Column(name = "quantity_after", nullable = false, updatable = false)
    private int quantityAfter;

    @Column(length = REASON_MAX_LENGTH, updatable = false)
    private String reason;

    /** Null once the user who made the movement has been deleted. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", updatable = false)
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public StockMovement(Product product, StockMovementType type, int quantityDelta, int quantityAfter,
            String reason, User createdBy) {
        this.organization = product.getOrganization();
        this.product = product;
        this.type = type;
        this.quantityDelta = quantityDelta;
        this.quantityAfter = quantityAfter;
        this.reason = normalizeReason(reason);
        this.createdBy = createdBy;
    }

    /** Trims a reason; blank or null means no reason. */
    public static String normalizeReason(String reason) {
        return reason == null || reason.isBlank() ? null : reason.trim();
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof StockMovement other && getId() != null && getId().equals(other.getId());
    }

    @Override
    public int hashCode() {
        return StockMovement.class.hashCode();
    }
}
