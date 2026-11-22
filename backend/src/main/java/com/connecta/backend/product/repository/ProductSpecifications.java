package com.connecta.backend.product.repository;

import com.connecta.backend.product.domain.Product;
import com.connecta.backend.product.domain.StockStatus;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * Specification pattern: each list filter is a small reusable predicate, combined with AND.
 * The stock rules mirror {@link StockStatus#of}; the repository tests check they agree.
 */
public final class ProductSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private ProductSpecifications() {
    }

    public static Specification<Product> inOrganization(UUID organizationId) {
        return (root, query, cb) -> cb.equal(root.get("organization").get("id"), organizationId);
    }

    public static Specification<Product> inCategory(UUID categoryId) {
        return (root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId);
    }

    /** Name or SKU contains the text, ignoring case. LIKE wildcards in the text match literally. */
    public static Specification<Product> nameOrSkuContains(String text) {
        String pattern = "%" + escapeLike(text.toLowerCase()) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("name")), pattern, LIKE_ESCAPE),
                cb.like(cb.lower(root.get("sku")), pattern, LIKE_ESCAPE));
    }

    public static Specification<Product> withStockStatus(StockStatus status) {
        return (root, query, cb) -> {
            var stock = root.<Integer>get("stockQuantity");
            var reorder = root.<Integer>get("reorderLevel");
            var out = cb.lessThanOrEqualTo(stock, 0);
            var low = cb.and(cb.greaterThan(stock, 0), cb.greaterThan(reorder, 0),
                    cb.lessThanOrEqualTo(stock, reorder));
            return switch (status) {
                case OUT_OF_STOCK -> out;
                case LOW_STOCK -> low;
                case IN_STOCK -> cb.not(cb.or(out, low));
            };
        };
    }

    private static String escapeLike(String text) {
        String escape = String.valueOf(LIKE_ESCAPE);
        return text.replace(escape, escape + escape)
                .replace("%", escape + "%")
                .replace("_", escape + "_");
    }
}
