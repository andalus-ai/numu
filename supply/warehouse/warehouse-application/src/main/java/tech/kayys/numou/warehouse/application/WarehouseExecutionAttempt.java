package tech.kayys.numou.warehouse.application;

import java.time.Instant;
import java.util.Objects;

/**
 * Durable journal entry for a cross-context warehouse/inventory command.
 *
 * <p>The fingerprint binds a tenant-scoped command key to one canonical payload.
 * Adapters must persist transitions with optimistic version checks and must not
 * overwrite a different fingerprint for an existing tenant/command key.</p>
 */
public record WarehouseExecutionAttempt(
        String tenantId,
        String commandKey,
        String fingerprint,
        String workOrderId,
        String lineId,
        String operation,
        WarehouseExecutionAttemptStatus status,
        int attemptCount,
        String lastError,
        Instant createdAt,
        Instant updatedAt,
        long version,
        WarehouseInventoryRecoveryPayload recoveryPayload) {

    public WarehouseExecutionAttempt {
        tenantId = required(tenantId, "tenantId");
        commandKey = required(commandKey, "commandKey");
        fingerprint = required(fingerprint, "fingerprint");
        workOrderId = required(workOrderId, "workOrderId");
        lineId = required(lineId, "lineId");
        operation = required(operation, "operation");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (attemptCount < 1 || version < 1 || updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("Invalid attempt counters or timestamps");
        }
        if (status == WarehouseExecutionAttemptStatus.NEEDS_RECONCILIATION
                && (lastError == null || lastError.isBlank())) {
            throw new IllegalArgumentException("A reconciliation attempt must explain the failure");
        }
    }

    public static WarehouseExecutionAttempt prepared(
            String tenantId, String commandKey, String fingerprint,
            String workOrderId, String lineId, String operation) {
        return prepared(tenantId, commandKey, fingerprint, workOrderId, lineId, operation, null);
    }

    public static WarehouseExecutionAttempt prepared(
            String tenantId, String commandKey, String fingerprint,
            String workOrderId, String lineId, String operation,
            WarehouseInventoryRecoveryPayload recoveryPayload) {
        Instant now = Instant.now();
        return new WarehouseExecutionAttempt(tenantId, commandKey, fingerprint, workOrderId, lineId,
                operation, WarehouseExecutionAttemptStatus.PREPARED, 1, null, now, now, 1, recoveryPayload);
    }

    public WarehouseExecutionAttempt inventoryPosted() {
        requireStatus(WarehouseExecutionAttemptStatus.PREPARED);
        return revise(WarehouseExecutionAttemptStatus.INVENTORY_POSTED, null);
    }

    public WarehouseExecutionAttempt warehouseCommitted() {
        if (status != WarehouseExecutionAttemptStatus.INVENTORY_POSTED
                && status != WarehouseExecutionAttemptStatus.WAREHOUSE_COMMITTED) {
            throw new IllegalStateException("Inventory must be posted before warehouse commit");
        }
        if (status == WarehouseExecutionAttemptStatus.WAREHOUSE_COMMITTED) return this;
        return revise(WarehouseExecutionAttemptStatus.WAREHOUSE_COMMITTED, null);
    }

    public WarehouseExecutionAttempt needsReconciliation(String reason) {
        if (status == WarehouseExecutionAttemptStatus.WAREHOUSE_COMMITTED) {
            throw new IllegalStateException("Committed execution cannot be marked for reconciliation");
        }
        return revise(WarehouseExecutionAttemptStatus.NEEDS_RECONCILIATION, required(reason, "reason"));
    }

    public WarehouseExecutionAttempt retryStarted() {
        if (status != WarehouseExecutionAttemptStatus.NEEDS_RECONCILIATION) {
            throw new IllegalStateException("Only reconciliation attempts can be retried");
        }
        Instant now = Instant.now();
        return new WarehouseExecutionAttempt(tenantId, commandKey, fingerprint, workOrderId, lineId, operation,
                WarehouseExecutionAttemptStatus.PREPARED, attemptCount + 1, null, createdAt, now, version + 1, recoveryPayload);
    }

    private WarehouseExecutionAttempt revise(WarehouseExecutionAttemptStatus next, String error) {
        Instant now = Instant.now();
        return new WarehouseExecutionAttempt(tenantId, commandKey, fingerprint, workOrderId, lineId, operation,
                next, attemptCount, error, createdAt, now, version + 1, recoveryPayload);
    }

    private void requireStatus(WarehouseExecutionAttemptStatus expected) {
        if (status != expected) throw new IllegalStateException("Expected " + expected + " but was " + status);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
