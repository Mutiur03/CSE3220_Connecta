package com.connecta.backend.product.dto;

import com.connecta.backend.product.domain.Product;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Body of PUT, a full replace: a null categoryId removes the product from its category.
 * A blank sku means none; a null unitPrice or reorderLevel means 0.
 */
public record UpdateProductRequest(
        @NotBlank @Size(max = Product.NAME_MAX_LENGTH) String name,
        @Size(max = Product.DESCRIPTION_MAX_LENGTH) String description,
        UUID categoryId,
        @Size(max = Product.SKU_MAX_LENGTH) String sku,
        @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal unitPrice,
        @Min(0) Integer reorderLevel) {

    public UpdateProductRequest {
        sku = Product.normalizeSku(sku);
        unitPrice = unitPrice == null ? BigDecimal.ZERO : unitPrice;
        reorderLevel = reorderLevel == null ? Integer.valueOf(0) : reorderLevel;
    }
}
