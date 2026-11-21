package com.connecta.backend.product.dto;

import com.connecta.backend.product.domain.Product;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Body of PUT, a full replace: a null categoryId removes the product from its category. */
public record UpdateProductRequest(
        @NotBlank @Size(max = Product.NAME_MAX_LENGTH) String name,
        @Size(max = Product.DESCRIPTION_MAX_LENGTH) String description,
        UUID categoryId) {
}
