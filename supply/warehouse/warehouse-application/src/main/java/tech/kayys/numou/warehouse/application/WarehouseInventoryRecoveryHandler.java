package tech.kayys.numou.warehouse.application;

import java.util.Objects;

/**
 * Replays persisted receiving/picking commands through the same coordinator and command key.
 * The inventory and warehouse adapters remain responsible for durable idempotency.
 */
public final class WarehouseInventoryRecoveryHandler implements WarehouseExecutionRecoveryWorker.RecoveryHandler {
    private final RecoverableWarehouseInventoryExecutionCoordinator coordinator;

    public WarehouseInventoryRecoveryHandler(RecoverableWarehouseInventoryExecutionCoordinator coordinator) {
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
    }

    @Override
    public void recover(WarehouseExecutionAttempt attempt) {
        Objects.requireNonNull(attempt, "attempt");
        WarehouseInventoryRecoveryPayload payload = attempt.recoveryPayload();
        if (payload == null) {
            throw new IllegalStateException("Recovery payload is missing for command " + attempt.commandKey());
        }
        switch (payload.operation()) {
            case "receive" -> coordinator.receive(attempt.tenantId(), attempt.workOrderId(), attempt.lineId(),
                    payload.itemId(), payload.warehouseId(), payload.quantityValue(), payload.unitCostValue(),
                    payload.currency(), attempt.commandKey());
            case "pick" -> coordinator.pick(attempt.tenantId(), attempt.workOrderId(), attempt.lineId(),
                    payload.itemId(), payload.warehouseId(), payload.reservationId(), payload.quantityValue(),
                    payload.currency(), attempt.commandKey());
            default -> throw new IllegalStateException("Unsupported recovery operation: " + payload.operation());
        }
    }
}
