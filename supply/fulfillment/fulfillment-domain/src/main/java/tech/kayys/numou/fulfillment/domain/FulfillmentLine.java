package tech.kayys.numou.fulfillment.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Quantity progression for one requested item. Item references and UOM are
 * intentionally neutral strings so the context does not depend on a catalog,
 * construction, commerce, or manufacturing module.
 */
public record FulfillmentLine(
        String lineId,
        String itemReference,
        BigDecimal requestedQuantity,
        BigDecimal allocatedQuantity,
        BigDecimal pickedQuantity,
        BigDecimal packedQuantity,
        String uom
) {
    public FulfillmentLine {
        lineId = requireText(lineId, "lineId");
        itemReference = requireText(itemReference, "itemReference");
        uom = requireText(uom, "uom");
        Objects.requireNonNull(requestedQuantity, "requestedQuantity is required");
        Objects.requireNonNull(allocatedQuantity, "allocatedQuantity is required");
        Objects.requireNonNull(pickedQuantity, "pickedQuantity is required");
        Objects.requireNonNull(packedQuantity, "packedQuantity is required");

        if (requestedQuantity.signum() <= 0) throw new IllegalArgumentException("Requested quantity must be positive");
        if (allocatedQuantity.signum() < 0 || pickedQuantity.signum() < 0 || packedQuantity.signum() < 0) {
            throw new IllegalArgumentException("Allocated, picked, and packed quantities cannot be negative");
        }
        if (allocatedQuantity.compareTo(requestedQuantity) > 0) {
            throw new IllegalArgumentException("Allocated quantity cannot exceed requested quantity");
        }
        if (pickedQuantity.compareTo(allocatedQuantity) > 0) {
            throw new IllegalArgumentException("Picked quantity cannot exceed allocated quantity");
        }
        if (packedQuantity.compareTo(pickedQuantity) > 0) {
            throw new IllegalArgumentException("Packed quantity cannot exceed picked quantity");
        }
    }

    public static FulfillmentLine requested(String lineId, String itemReference,
                                            BigDecimal quantity, String uom) {
        return new FulfillmentLine(lineId, itemReference, quantity,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, uom);
    }

    public FulfillmentLine allocate(BigDecimal quantity) {
        requirePositive(quantity, "Allocation quantity");
        BigDecimal next = allocatedQuantity.add(quantity);
        if (next.compareTo(requestedQuantity) > 0) {
            throw new IllegalArgumentException("Allocation exceeds requested quantity for line " + lineId);
        }
        return new FulfillmentLine(lineId, itemReference, requestedQuantity, next, pickedQuantity, packedQuantity, uom);
    }

    public FulfillmentLine pick(BigDecimal quantity) {
        requirePositive(quantity, "Picked quantity");
        BigDecimal next = pickedQuantity.add(quantity);
        if (next.compareTo(allocatedQuantity) > 0) {
            throw new IllegalArgumentException("Picked quantity exceeds allocated quantity for line " + lineId);
        }
        return new FulfillmentLine(lineId, itemReference, requestedQuantity, allocatedQuantity, next, packedQuantity, uom);
    }

    public FulfillmentLine pack(BigDecimal quantity) {
        requirePositive(quantity, "Packed quantity");
        BigDecimal next = packedQuantity.add(quantity);
        if (next.compareTo(pickedQuantity) > 0) {
            throw new IllegalArgumentException("Packed quantity exceeds picked quantity for line " + lineId);
        }
        return new FulfillmentLine(lineId, itemReference, requestedQuantity, allocatedQuantity, pickedQuantity, next, uom);
    }

    private static void requirePositive(BigDecimal quantity, String label) {
        Objects.requireNonNull(quantity, label + " is required");
        if (quantity.signum() <= 0) throw new IllegalArgumentException(label + " must be positive");
    }

    private static String requireText(String value, String label) {
        Objects.requireNonNull(value, label + " is required");
        String normalized = value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(label + " must not be blank");
        return normalized;
    }
}
