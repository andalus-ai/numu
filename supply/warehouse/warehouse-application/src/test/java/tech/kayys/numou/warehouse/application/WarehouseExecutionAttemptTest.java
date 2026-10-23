package tech.kayys.numou.warehouse.application;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WarehouseExecutionAttemptTest {
    @Test
    void inventoryMustPrecedeWarehouseCommit() {
        WarehouseExecutionAttempt attempt = prepared();
        assertThrows(IllegalStateException.class, attempt::warehouseCommitted);
        assertEquals(WarehouseExecutionAttemptStatus.INVENTORY_POSTED, attempt.inventoryPosted().status());
        assertEquals(WarehouseExecutionAttemptStatus.WAREHOUSE_COMMITTED,
                attempt.inventoryPosted().warehouseCommitted().status());
    }

    @Test
    void failedAttemptCanBeRetriedButCommittedAttemptCannotBeReconciled() {
        WarehouseExecutionAttempt attempt = prepared().needsReconciliation("warehouse commit timed out");
        assertEquals(WarehouseExecutionAttemptStatus.NEEDS_RECONCILIATION, attempt.status());
        WarehouseExecutionAttempt retry = attempt.retryStarted();
        assertEquals(WarehouseExecutionAttemptStatus.PREPARED, retry.status());
        assertEquals(2, retry.attemptCount());
        assertEquals(attempt.version() + 1, retry.version());
        assertThrows(IllegalStateException.class,
                () -> prepared().inventoryPosted().warehouseCommitted().needsReconciliation("late timeout"));
    }

    @Test
    void reconciliationRequiresReasonAndPayloadIdentity() {
        assertThrows(IllegalArgumentException.class, () -> prepared().needsReconciliation(" "));
        WarehouseExecutionAttempt a = prepared();
        UnusedRepository repository = new UnusedRepository();
        repository.createIfAbsent(a);
        assertThrows(IllegalStateException.class, () ->
                new WarehouseExecutionRecoveryService(repository)
                        .prepare("tenant-a", "command-a", "different-fingerprint", "work-1", "line-1", "RECEIVE"));
    }

    private WarehouseExecutionAttempt prepared() {
        return WarehouseExecutionAttempt.prepared("tenant-a", "command-a", "sha256:payload",
                "work-1", "line-1", "RECEIVE");
    }

    private static final class UnusedRepository implements WarehouseExecutionAttemptRepository {
        private WarehouseExecutionAttempt stored;
        @Override public WarehouseExecutionAttempt createIfAbsent(WarehouseExecutionAttempt attempt) {
            if (stored == null) stored = attempt;
            return stored;
        }
        @Override public java.util.Optional<WarehouseExecutionAttempt> find(String tenant, String key) {
            return java.util.Optional.ofNullable(stored);
        }
        @Override public WarehouseExecutionAttempt transition(String tenant, String key, long version,
                                                               WarehouseExecutionAttempt candidate) {
            if (stored.version() != version) throw new IllegalStateException("optimistic lock");
            stored = candidate;
            return stored;
        }
        @Override public java.util.List<WarehouseExecutionAttempt> findRecoverable(int limit) {
            return java.util.List.of();
        }
    }
}
