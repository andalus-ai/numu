package tech.kayys.numou.warehouse.application;

/** Durable progress states for a warehouse operation that also mutates inventory. */
public enum WarehouseExecutionAttemptStatus {
    PREPARED,
    INVENTORY_POSTED,
    WAREHOUSE_COMMITTED,
    NEEDS_RECONCILIATION
}
