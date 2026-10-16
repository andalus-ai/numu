package tech.kayys.syirkah.inventory.domain.inventory;

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
}
