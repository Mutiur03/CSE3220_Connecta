package com.connecta.backend.product.dto;

import com.connecta.backend.product.domain.StockMovement;
import com.connecta.backend.product.domain.StockMovementType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** A stock movement to record. The quantity rule per type (at least 1, or 0 for ADJUST) is checked by the domain. */
public record RecordStockMovementRequest(
        @NotNull StockMovementType type,
        @NotNull @Min(0) Integer quantity,
        @Size(max = StockMovement.REASON_MAX_LENGTH) String reason) {
}
