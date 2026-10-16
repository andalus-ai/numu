package tech.kayys.syirkah.inventory.domain.inventory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Immutable inventory ledger entry. Stock quantities are derived from movements.
 */
public record InventoryMovement(
        MovementId id,
        ItemId itemId,
        WarehouseId warehouseId,
        MovementType type,
        BigDecimal quantity,
        BigDecimal unitCost,
        String currency,
        String referenceNumber,
        Instant occurredAt
) {
    public InventoryMovement {
        Objects.requireNonNull(id);
        Objects.requireNonNull(itemId);
        Objects.requireNonNull(warehouseId);
        Objects.requireNonNull(type);
        Objects.requireNonNull(quantity);
        Objects.requireNonNull(unitCost);
        Objects.requireNonNull(currency);
        Objects.requireNonNull(occurredAt);
        if (quantity.signum() == 0) throw new IllegalArgumentException("Quantity must not be zero");
    }

    public BigDecimal totalCost() {
        return quantity.abs().multiply(unitCost);
    }
}
