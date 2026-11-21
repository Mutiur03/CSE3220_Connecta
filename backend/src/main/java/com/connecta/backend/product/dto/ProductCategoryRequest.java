package com.connecta.backend.product.dto;

import com.connecta.backend.product.domain.ProductCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body for create and full update. Limits mirror the product_categories columns. */
public record ProductCategoryRequest(
        @NotBlank @Size(max = ProductCategory.NAME_MAX_LENGTH) String name,
        @Size(max = ProductCategory.DESCRIPTION_MAX_LENGTH) String description) {
}
