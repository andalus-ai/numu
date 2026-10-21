package tech.kayys.numou.purchasing.domain.procurement;

import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.math.BigDecimal;
import java.util.Objects;

public record PoLine(
        String lineId,
        String itemCode,
        String itemDescription,
        BigDecimal orderedQuantity,
        BigDecimal receivedQuantity,
        Money unitPrice
) {
    public PoLine {
        Objects.requireNonNull(lineId, "lineId cannot be null");
        Objects.requireNonNull(itemCode, "itemCode cannot be null");
        Objects.requireNonNull(itemDescription, "itemDescription cannot be null");
        Objects.requireNonNull(orderedQuantity, "orderedQuantity cannot be null");
        Objects.requireNonNull(receivedQuantity, "receivedQuantity cannot be null");
        Objects.requireNonNull(unitPrice, "unitPrice cannot be null");

        if (lineId.isBlank()) {
            throw new IllegalArgumentException("lineId must not be blank");
        }
        if (itemCode.isBlank()) {
            throw new IllegalArgumentException("itemCode must not be blank");
        }
        if (orderedQuantity.signum() <= 0) {
            throw new IllegalArgumentException("orderedQuantity must be positive");
        }
        if (receivedQuantity.signum() < 0) {
            throw new IllegalArgumentException("receivedQuantity must not be negative");
        }
        if (receivedQuantity.compareTo(orderedQuantity) > 0) {
            throw new IllegalArgumentException("receivedQuantity must not exceed orderedQuantity");
        }
    }

    public static PoLine of(String lineId, String itemCode, String description, BigDecimal qty, Money unitPrice) {
        return new PoLine(lineId, itemCode, description, qty, BigDecimal.ZERO, unitPrice);
    }

    public Money lineTotal() {
        return unitPrice.multiply(orderedQuantity);
    }

    /**
     * Returns a new line with a positive receipt delta. Over-receipt is rejected;
     * any tolerance must be an explicit policy above this domain object.
     */
    public PoLine withReceived(BigDecimal addQty) {
        Objects.requireNonNull(addQty, "addQty cannot be null");
        if (addQty.signum() <= 0) {
            throw new IllegalArgumentException("Receipt quantity must be positive");
        }
        BigDecimal nextReceived = receivedQuantity.add(addQty);
        if (nextReceived.compareTo(orderedQuantity) > 0) {
            throw new IllegalArgumentException("Receipt quantity exceeds remaining ordered quantity");
        }
        return new PoLine(lineId, itemCode, itemDescription, orderedQuantity, nextReceived, unitPrice);
    }

    public boolean isFullyReceived() {
        return receivedQuantity.compareTo(orderedQuantity) == 0;
    }
}
