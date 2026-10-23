package tech.kayys.numou.warehouse.application;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Minimal immutable command data required to replay an inventory-first warehouse operation
 * after process restart. Decimal values are stored as plain strings to avoid JSON/SQL
 * floating-point conversions.
 */
public record WarehouseInventoryRecoveryPayload(
        String operation,
        String itemId,
        String warehouseId,
        String reservationId,
        String quantity,
        String unitCost,
        String currency,
        String reference) {

    public WarehouseInventoryRecoveryPayload {
        operation = required(operation, "operation");
        if (!operation.equals("receive") && !operation.equals("pick"))
            throw new IllegalArgumentException("operation must be receive or pick");
        itemId = required(itemId, "itemId");
        warehouseId = required(warehouseId, "warehouseId");
        quantity = decimal(quantity, "quantity", true);
        unitCost = unitCost == null ? null : decimal(unitCost, "unitCost", false);
        currency = required(currency, "currency");
        reference = required(reference, "reference");
        if ("receive".equals(operation) && unitCost == null)
            throw new IllegalArgumentException("unitCost is required for receiving");
        if ("pick".equals(operation) && (reservationId == null || reservationId.isBlank()))
            throw new IllegalArgumentException("reservationId is required for picking");
        reservationId = reservationId == null || reservationId.isBlank() ? null : reservationId.trim();
    }

    public BigDecimal quantityValue() { return new BigDecimal(quantity); }
    public BigDecimal unitCostValue() { return unitCost == null ? null : new BigDecimal(unitCost); }

    private static String decimal(String value, String field, boolean positive) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        BigDecimal parsed;
        try { parsed = new BigDecimal(value.trim()); }
        catch (NumberFormatException e) { throw new IllegalArgumentException(field + " must be decimal", e); }
        if (positive ? parsed.signum() <= 0 : parsed.signum() < 0)
            throw new IllegalArgumentException(field + (positive ? " must be positive" : " cannot be negative"));
        return parsed.toPlainString();
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
