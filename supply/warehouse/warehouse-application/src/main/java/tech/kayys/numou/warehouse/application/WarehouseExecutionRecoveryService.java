package tech.kayys.numou.warehouse.application;

import java.util.List;
import java.util.Objects;

/**
 * Coordinates durable state transitions around inventory-first warehouse execution.
 * Physical retry remains the caller's responsibility: replay the same tenant, command key,
 * and payload so the inventory owner can return its original movement.
 */
public final class WarehouseExecutionRecoveryService {
    private final WarehouseExecutionAttemptRepository repository;

    public WarehouseExecutionRecoveryService(WarehouseExecutionAttemptRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public WarehouseExecutionAttempt prepare(String tenantId, String commandKey, String fingerprint,
                                             String workOrderId, String lineId, String operation) {
        return prepare(tenantId, commandKey, fingerprint, workOrderId, lineId, operation, null);
    }

    public WarehouseExecutionAttempt prepare(String tenantId, String commandKey, String fingerprint,
            String workOrderId, String lineId, String operation,
            WarehouseInventoryRecoveryPayload recoveryPayload) {
        WarehouseExecutionAttempt proposed = WarehouseExecutionAttempt.prepared(
                tenantId, commandKey, fingerprint, workOrderId, lineId, operation, recoveryPayload);
        WarehouseExecutionAttempt stored = repository.createIfAbsent(proposed);
        assertSamePayload(stored, fingerprint, workOrderId, lineId, operation, recoveryPayload);
        return stored;
    }

    public WarehouseExecutionAttempt inventoryPosted(String tenantId, String commandKey) {
        return transition(tenantId, commandKey, WarehouseExecutionAttempt::inventoryPosted);
    }

    public WarehouseExecutionAttempt warehouseCommitted(String tenantId, String commandKey) {
        return transition(tenantId, commandKey, WarehouseExecutionAttempt::warehouseCommitted);
    }

    public WarehouseExecutionAttempt failed(String tenantId, String commandKey, String reason) {
        return transition(tenantId, commandKey, a -> a.needsReconciliation(reason));
    }

    public WarehouseExecutionAttempt retry(String tenantId, String commandKey) {
        return transition(tenantId, commandKey, WarehouseExecutionAttempt::retryStarted);
    }

    public List<WarehouseExecutionAttempt> recoverable(int limit) {
        if (limit < 1 || limit > 1000) throw new IllegalArgumentException("limit must be between 1 and 1000");
        return List.copyOf(repository.findRecoverable(limit));
    }

    private WarehouseExecutionAttempt transition(String tenantId, String commandKey,
                                                   java.util.function.UnaryOperator<WarehouseExecutionAttempt> change) {
        if (tenantId == null || tenantId.isBlank() || commandKey == null || commandKey.isBlank()) {
            throw new IllegalArgumentException("tenantId and commandKey are required");
        }
        WarehouseExecutionAttempt current = repository.find(tenantId.trim(), commandKey.trim())
                .orElseThrow(() -> new IllegalArgumentException("Execution attempt not found"));
        WarehouseExecutionAttempt candidate = change.apply(current);
        if (candidate == current) return current;
        return repository.transition(tenantId.trim(), commandKey.trim(), current.version(), candidate);
    }

    private static void assertSamePayload(WarehouseExecutionAttempt stored, String fingerprint,
                                          String workOrderId, String lineId, String operation,
                                          WarehouseInventoryRecoveryPayload recoveryPayload) {
        if (!stored.fingerprint().equals(fingerprint)
                || !stored.workOrderId().equals(workOrderId)
                || !stored.lineId().equals(lineId)
                || !stored.operation().equals(operation)
                || !java.util.Objects.equals(stored.recoveryPayload(), recoveryPayload)) {
            throw new IllegalStateException("Command key reused with different execution payload");
        }
    }
}
