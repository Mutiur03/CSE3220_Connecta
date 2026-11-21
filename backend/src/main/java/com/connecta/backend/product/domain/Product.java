package com.connecta.backend.product.domain;

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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A product sold or listed by one organization. The category is optional. */
@Entity
@Table(name = "products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

    public static final int NAME_MAX_LENGTH = 150;
    public static final int DESCRIPTION_MAX_LENGTH = 1000;
    public static final int SKU_MAX_LENGTH = 64;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false, updatable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private ProductCategory category;

    @Column(nullable = false, length = NAME_MAX_LENGTH)
    private String name;

    @Column(length = DESCRIPTION_MAX_LENGTH)
    private String description;

    @Column(length = SKU_MAX_LENGTH)
    private String sku;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    /** Changed only by {@link #applyMovement}, so every change has a recorded stock movement. */
    @Column(name = "stock_quantity", nullable = false)
    private int stockQuantity;

    @Column(name = "reorder_level", nullable = false)
    private int reorderLevel;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Product(Organization organization, ProductCategory category, String name, String description) {
        this(organization, category, name, description, null, BigDecimal.ZERO, 0);
    }

    public Product(Organization organization, ProductCategory category, String name, String description,
            String sku, BigDecimal unitPrice, int reorderLevel) {
        this.organization = organization;
        this.category = category;
        this.name = name.trim();
        this.description = description;
        this.sku = normalizeSku(sku);
        this.unitPrice = unitPrice;
        this.reorderLevel = reorderLevel;
    }

    /** Trims a SKU; blank or null means no SKU. */
    public static String normalizeSku(String sku) {
        return sku == null || sku.isBlank() ? null : sku.trim();
    }

    public StockStatus stockStatus() {
        return StockStatus.of(stockQuantity, reorderLevel);
    }

    /** Moves the product to a category, or removes it from its category when null. */
    public void changeCategory(ProductCategory newCategory) {
        this.category = newCategory;
    }

    /**
     * Applies one stock movement and returns the change it made (negative for an ISSUE). The only place
     * stock changes. The caller must hold the row lock (ProductRepository.findForUpdateByIdAndOrganizationId).
     */
    public int applyMovement(StockMovementType type, int quantity) {
        if (quantity < type.minimumQuantity()) {
            throw new InvalidStockQuantityException(type + " needs a quantity of at least " + type.minimumQuantity());
        }
        int before = stockQuantity;
        stockQuantity = switch (type) {
            case RECEIVE -> addStock(before, quantity);
            case ISSUE -> removeStock(before, quantity);
            case ADJUST -> quantity;
        };
        return stockQuantity - before;
    }

    private static int addStock(int before, int quantity) {
        long total = (long) before + quantity;
        if (total > Integer.MAX_VALUE) {
            throw new InvalidStockQuantityException("Stock cannot exceed " + Integer.MAX_VALUE);
        }
        return (int) total;
    }

    private static int removeStock(int before, int quantity) {
        if (quantity > before) {
            throw new InsufficientStockException();
        }
        return before - quantity;
    }

    public void update(String newName, String newDescription, String newSku, BigDecimal newUnitPrice,
            int newReorderLevel) {
        this.name = newName.trim();
        this.description = newDescription;
        this.sku = normalizeSku(newSku);
        this.unitPrice = newUnitPrice;
        this.reorderLevel = newReorderLevel;
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
        return this == o || o instanceof Product other && getId() != null && getId().equals(other.getId());
    }

    @Override
    public int hashCode() {
        return Product.class.hashCode();
    }
}
