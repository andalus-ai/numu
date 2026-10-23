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

    @Test void payloadSurvivesJournalStateTransitionsAndCannotBeChangedForSameKey() {
        var payload = new WarehouseInventoryRecoveryPayload("pick", "item-1", "wh-1", "reservation-1",
                "2.00", null, "IDR", "pick-1");
        var attempt = WarehouseExecutionAttempt.prepared("tenant", "command", "fingerprint",
                "work", "line", "pick", payload);
        assertEquals(payload, attempt.inventoryPosted().recoveryPayload());
        assertEquals(payload, attempt.inventoryPosted().warehouseCommitted().recoveryPayload());

        var otherPayload = new WarehouseInventoryRecoveryPayload("pick", "item-2", "wh-1", "reservation-1",
                "2.00", null, "IDR", "pick-1");
        var repository = new TestAttempts(attempt);
        var recovery = new WarehouseExecutionRecoveryService(repository);
        assertThrows(IllegalStateException.class, () -> recovery.prepare("tenant", "command", "fingerprint",
                "work", "line", "pick", otherPayload));
    }

    @Test void receivingRequiresCostAndUnsupportedOperationsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new WarehouseInventoryRecoveryPayload(
                "receive", "item-1", "wh-1", null, "1", null, "IDR", "receipt-1"));
        assertThrows(IllegalArgumentException.class, () -> new WarehouseInventoryRecoveryPayload(
                "adjust", "item-1", "wh-1", null, "1", "1", "IDR", "adjust-1"));
    }

    private static final class TestAttempts implements WarehouseExecutionAttemptRepository {
        private WarehouseExecutionAttempt stored;
        private TestAttempts(WarehouseExecutionAttempt stored) { this.stored = stored; }
        @Override public WarehouseExecutionAttempt createIfAbsent(WarehouseExecutionAttempt attempt) { return stored; }
        @Override public java.util.Optional<WarehouseExecutionAttempt> find(String tenant, String key) {
            return java.util.Optional.of(stored);
        }
        @Override public WarehouseExecutionAttempt transition(String tenant, String key, long version,
                WarehouseExecutionAttempt candidate) { stored = candidate; return candidate; }
        @Override public java.util.List<WarehouseExecutionAttempt> findRecoverable(int limit) {
            return java.util.List.of(stored);
        }
    }
}
