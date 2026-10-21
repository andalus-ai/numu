package tech.kayys.numou.inventory.domain.inventory;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class InventoryMovementTest {

    @Test
    void calculate_total_cost_correctly() {
        var movement = new InventoryMovement(
                MovementId.generate(), ItemId.generate(), WarehouseId.generate(),
                MovementType.RECEIPT, new BigDecimal("10"), new BigDecimal("15.50"),
                "USD", "PO-100", Instant.now());

        assertEquals(new BigDecimal("155.00"), movement.totalCost());
    }

    @Test
    void rejects_zero_quantity() {
        assertThrows(IllegalArgumentException.class, () ->
                new InventoryMovement(
                        MovementId.generate(), ItemId.generate(), WarehouseId.generate(),
                        MovementType.RECEIPT, BigDecimal.ZERO, new BigDecimal("10.00"),
                        "USD", "PO-100", Instant.now()));
    }

    @Test
    void rejects_negative_unit_cost() {
        assertThrows(IllegalArgumentException.class, () ->
                new InventoryMovement(
                        MovementId.generate(), ItemId.generate(), WarehouseId.generate(),
                        MovementType.RECEIPT, BigDecimal.ONE, new BigDecimal("-0.01"),
                        "USD", "PO-101", Instant.now()));
    }

    @Test
    void rejects_blank_or_malformed_currency_code() {
        assertThrows(IllegalArgumentException.class, () ->
                new InventoryMovement(
                        MovementId.generate(), ItemId.generate(), WarehouseId.generate(),
                        MovementType.RECEIPT, BigDecimal.ONE, BigDecimal.ONE,
                        "  ", "PO-102", Instant.now()));

        assertThrows(IllegalArgumentException.class, () ->
                new InventoryMovement(
                        MovementId.generate(), ItemId.generate(), WarehouseId.generate(),
                        MovementType.RECEIPT, BigDecimal.ONE, BigDecimal.ONE,
                        "US", "PO-103", Instant.now()));
    }

    @Test
    void normalizes_currency_code_without_locale_dependent_behavior() {
        var movement = new InventoryMovement(
                MovementId.generate(), ItemId.generate(), WarehouseId.generate(),
                MovementType.RECEIPT, BigDecimal.ONE, BigDecimal.ONE,
                " idr ", "PO-104", Instant.now());

        assertEquals("IDR", movement.currency());
    }
}
