package tech.kayys.numou.warehouse.application;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class WarehouseInventoryRecoveryPayloadTest {
    @Test void receivingPayloadPreservesExactDecimalTextAndValues() {
        var payload = new WarehouseInventoryRecoveryPayload("receive", "item-1", "wh-1", null,
                "12.500", "0.00", "IDR", "receipt-1");
        assertEquals("12.500", payload.quantity());
        assertEquals(new BigDecimal("12.500"), payload.quantityValue());
        assertEquals(new BigDecimal("0.00"), payload.unitCostValue());
    }

    @Test void pickingRequiresReservationAndPositiveQuantity() {
        assertThrows(IllegalArgumentException.class, () -> new WarehouseInventoryRecoveryPayload(
                "pick", "item-1", "wh-1", null, "1", null, "IDR", "pick-1"));
        assertThrows(IllegalArgumentException.class, () -> new WarehouseInventoryRecoveryPayload(
                "pick", "item-1", "wh-1", "reservation-1", "0", null, "IDR", "pick-1"));
    }

    @Test void receivingRequiresCostAndUnsupportedOperationsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new WarehouseInventoryRecoveryPayload(
                "receive", "item-1", "wh-1", null, "1", null, "IDR", "receipt-1"));
        assertThrows(IllegalArgumentException.class, () -> new WarehouseInventoryRecoveryPayload(
                "adjust", "item-1", "wh-1", null, "1", "1", "IDR", "adjust-1"));
    }
}
