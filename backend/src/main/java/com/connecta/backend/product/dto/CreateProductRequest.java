package com.connecta.backend.product.dto;

import com.connecta.backend.product.domain.Product;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Body of POST. The category is optional; when given it must belong to the same organization. */
public record CreateProductRequest(
        @NotBlank @Size(max = Product.NAME_MAX_LENGTH) String name,
        @Size(max = Product.DESCRIPTION_MAX_LENGTH) String description,
        UUID categoryId) {
}
