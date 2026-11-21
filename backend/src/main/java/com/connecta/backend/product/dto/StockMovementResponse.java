package com.connecta.backend.product.dto;

import com.connecta.backend.product.domain.StockMovement;
import com.connecta.backend.product.domain.StockMovementType;
import com.connecta.backend.user.domain.User;
import java.time.Instant;
import java.util.UUID;

/** A stock movement as the API shows it. createdBy is null when the user who made it no longer exists. */
public record StockMovementResponse(
        UUID id,
        UUID productId,
        String productName,
        StockMovementType type,
        int quantityDelta,
        int quantityAfter,
        String reason,
        Actor createdBy,
        Instant createdAt) {

    /** The user who recorded the movement: id and name only, never the account. */
    public record Actor(UUID id, String fullName) {
    }

    public static StockMovementResponse from(StockMovement movement) {
        User actor = movement.getCreatedBy();
        return new StockMovementResponse(
                movement.getId(),
                movement.getProduct().getId(),
                movement.getProduct().getName(),
                movement.getType(),
                movement.getQuantityDelta(),
                movement.getQuantityAfter(),
                movement.getReason(),
                actor == null ? null : new Actor(actor.getId(), actor.getFullName()),
                movement.getCreatedAt());
    }
}
