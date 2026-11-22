package com.connecta.backend.product.service;

import com.connecta.backend.product.domain.StockStatus;
import java.util.UUID;

/** The optional list filters; a null part is not applied. */
public record ProductFilter(String text, UUID categoryId, StockStatus stockStatus) {
}
