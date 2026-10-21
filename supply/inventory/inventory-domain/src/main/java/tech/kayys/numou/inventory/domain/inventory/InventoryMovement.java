package tech.kayys.numou.inventory.domain.inventory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

/**
 * Immutable inventory ledger entry. Stock quantities are derived from movements.
 *
 * <p>The ledger stores currency as an ISO-4217-shaped code so adapters do not
 * persist locale-dependent or malformed monetary metadata. Currency conversion
 * and cross-currency valuation belong to explicit application policies.</p>
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
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(warehouseId, "warehouseId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(unitCost, "unitCost");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(occurredAt, "occurredAt");

        if (quantity.signum() == 0) {
            throw new IllegalArgumentException("Quantity must not be zero");
        }
        if (unitCost.signum() < 0) {
            throw new IllegalArgumentException("Unit cost must not be negative");
        }

        currency = currency.trim().toUpperCase(Locale.ROOT);
        if (!currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("Currency must be a three-letter code");
        }
    }

    public BigDecimal totalCost() {
        return quantity.abs().multiply(unitCost);
    }
}
