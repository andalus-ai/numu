package tech.kayys.numou.quantity.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Commercially neutral quantity-schedule row, optionally linked to a generic
 * breakdown node. Rates, currency, revision status and approvals intentionally
 * remain outside this reusable primitive.
 */
public record QuantityScheduleLine(
        UUID id,
        UUID breakdownNodeId,
        String code,
        String description,
        Quantity quantity,
        int position) {

    public QuantityScheduleLine {
        Objects.requireNonNull(id, "Quantity schedule line id cannot be null");
        Objects.requireNonNull(breakdownNodeId, "Breakdown node id cannot be null");
        code = requireText(code, "Line code");
        description = requireText(description, "Line description");
        Objects.requireNonNull(quantity, "Line quantity cannot be null");
        if (position < 0) {
            throw new IllegalArgumentException("Line position cannot be negative");
        }
    }

    public static QuantityScheduleLine create(
            UUID breakdownNodeId,
            String code,
            String description,
            Quantity quantity,
            int position) {
        return new QuantityScheduleLine(
                UUID.randomUUID(), breakdownNodeId, code, description, quantity, position);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return value.trim();
    }
}
