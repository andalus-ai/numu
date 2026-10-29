package tech.kayys.numou.pos.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * A sale-time snapshot of an item reference, display details, quantity and unit price.
 * Catalog and pricing remain the source of truth; this snapshot preserves what was sold.
 */
public record SaleLine(
        UUID id,
        String itemReference,
        String displayName,
        String sku,
        BigDecimal quantity,
        String unitOfMeasure,
        Money unitPrice) {

    public SaleLine {
        Objects.requireNonNull(id, "line id cannot be null");
        Objects.requireNonNull(itemReference, "item reference cannot be null");
        Objects.requireNonNull(displayName, "display name cannot be null");
        Objects.requireNonNull(quantity, "quantity cannot be null");
        Objects.requireNonNull(unitOfMeasure, "unit of measure cannot be null");
        Objects.requireNonNull(unitPrice, "unit price cannot be null");
        itemReference = itemReference.trim();
        displayName = displayName.trim();
        unitOfMeasure = unitOfMeasure.trim();
        if (itemReference.isEmpty()) {
            throw new IllegalArgumentException("item reference cannot be blank");
        }
        if (displayName.isEmpty()) {
            throw new IllegalArgumentException("display name cannot be blank");
        }
        if (unitOfMeasure.isEmpty()) {
            throw new IllegalArgumentException("unit of measure cannot be blank");
        }
        if (quantity.signum() <= 0) {
            throw new IllegalArgumentException("quantity must be greater than zero");
        }
        if (unitPrice.isNegative()) {
            throw new IllegalArgumentException("unit price cannot be negative");
        }
        if (sku != null) {
            sku = sku.trim();
            if (sku.isEmpty()) {
                sku = null;
            }
        }
    }

    /**
     * Returns the unrounded extended amount. Rounding policy is owned by pricing/tax
     * and must be applied by the checkout application workflow.
     */
    public Money extendedAmount() {
        return new Money(unitPrice.amount().multiply(quantity), unitPrice.currency());
    }
}
