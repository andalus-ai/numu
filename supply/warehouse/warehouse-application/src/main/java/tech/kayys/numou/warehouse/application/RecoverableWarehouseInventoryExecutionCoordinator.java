package tech.kayys.numou.warehouse.application;

import tech.kayys.numou.warehouse.domain.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Recovery-aware inventory/warehouse coordinator.
 *
 * <p>The inventory command and warehouse command reuse the same tenant-scoped idempotency
 * key across retries. The durable attempt journal records the cross-context boundary so a
 * restart can safely resume from PREPARED or INVENTORY_POSTED without double-posting stock.</p>
 */
public final class RecoverableWarehouseInventoryExecutionCoordinator {
    private final WarehouseWorkRepository repository;
    private final WarehouseExecutionService warehouse;
    private final InventoryWarehouseGateway inventory;
    private final WarehouseExecutionRecoveryService recovery;

    public RecoverableWarehouseInventoryExecutionCoordinator(WarehouseWorkRepository repository,
            WarehouseExecutionService warehouse, InventoryWarehouseGateway inventory,
            WarehouseExecutionRecoveryService recovery) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.warehouse = Objects.requireNonNull(warehouse, "warehouse");
        this.inventory = Objects.requireNonNull(inventory, "inventory");
        this.recovery = Objects.requireNonNull(recovery, "recovery");
    }

    public WarehouseWorkOrder receive(String tenant, String workOrderId, String lineId, String itemId,
            String warehouseId, BigDecimal quantity, BigDecimal unitCost, String currency, String commandKey) {
        WarehouseWorkOrder order = load(tenant, workOrderId);
        if (order.type() != WarehouseWorkType.RECEIVING)
            throw new IllegalStateException("Inventory receipt requires a RECEIVING work order");
        String fingerprint = fingerprint("receive", tenant, workOrderId, lineId, itemId, warehouseId,
                quantity, unitCost, currency);
        return execute(order, lineId, quantity, null, commandKey, fingerprint, "receive",
                () -> inventory.receive(tenant, itemId, warehouseId, quantity, unitCost, currency,
                        order.reference() + ":" + lineId, commandKey, workOrderId, lineId));
    }

    public WarehouseWorkOrder pick(String tenant, String workOrderId, String lineId, String itemId,
            String warehouseId, String reservationId, BigDecimal quantity, String currency, String commandKey) {
        WarehouseWorkOrder order = load(tenant, workOrderId);
        if (order.type() != WarehouseWorkType.PICKING)
            throw new IllegalStateException("Reserved issue requires a PICKING work order");
        String fingerprint = fingerprint("pick", tenant, workOrderId, lineId, itemId, warehouseId,
                reservationId, quantity, currency);
        return execute(order, lineId, quantity, warehouseId, commandKey, fingerprint, "pick",
                () -> inventory.issueReserved(tenant, reservationId, itemId, warehouseId, quantity, currency,
                        order.reference() + ":" + lineId, commandKey, workOrderId, lineId));
    }

    private WarehouseWorkOrder execute(WarehouseWorkOrder order, String lineId, BigDecimal quantity,
            String context, String commandKey, String fingerprint, String operation, Runnable postInventory) {
        if (commandKey == null || commandKey.isBlank()) throw new IllegalArgumentException("commandKey required");
        String tenant = order.tenantId();
        WarehouseExecutionAttempt attempt = recovery.prepare(tenant, commandKey, fingerprint,
                order.id(), lineId, operation);

        if (attempt.status() == WarehouseExecutionAttemptStatus.WAREHOUSE_COMMITTED) {
            return repository.find(tenant, order.id())
                    .orElseThrow(() -> new IllegalStateException("Committed warehouse work order is missing"));
        }
        boolean retrying = attempt.status() == WarehouseExecutionAttemptStatus.NEEDS_RECONCILIATION
                || attempt.attemptCount() > 1
                || attempt.status() == WarehouseExecutionAttemptStatus.INVENTORY_POSTED;
        if (attempt.status() == WarehouseExecutionAttemptStatus.NEEDS_RECONCILIATION) {
            attempt = recovery.retry(tenant, commandKey);
        }
        try {
            if (attempt.status() == WarehouseExecutionAttemptStatus.PREPARED) {
                // Do not reject a retry if the warehouse commit succeeded before a process crash.
                if (!retrying) validate(order, lineId, quantity,
                        "receive".equals(operation) ? WarehouseWorkType.RECEIVING : WarehouseWorkType.PICKING);
                // Safe to replay if inventory posted but the process crashed before journaling it.
                postInventory.run();
                attempt = recovery.inventoryPosted(tenant, commandKey);
            }
            if (attempt.status() != WarehouseExecutionAttemptStatus.INVENTORY_POSTED)
                throw new IllegalStateException("Unexpected recovery state: " + attempt.status());
            WarehouseWorkOrder committed = warehouse.execute(tenant, order.id(), lineId, quantity, context, commandKey);
            recovery.warehouseCommitted(tenant, commandKey);
            return committed;
        } catch (RuntimeException failure) {
            try {
                WarehouseExecutionAttempt current = recovery.prepare(tenant, commandKey, fingerprint,
                        order.id(), lineId, operation);
                if (current.status() != WarehouseExecutionAttemptStatus.WAREHOUSE_COMMITTED
                        && current.status() != WarehouseExecutionAttemptStatus.NEEDS_RECONCILIATION) {
                    recovery.failed(tenant, commandKey, safeMessage(failure));
                }
            } catch (RuntimeException journalFailure) {
                failure.addSuppressed(journalFailure);
            }
            throw failure;
        }
    }

    private WarehouseWorkOrder load(String tenant, String id) {
        if (tenant == null || tenant.isBlank() || id == null || id.isBlank())
            throw new IllegalArgumentException("Tenant and work order id are required");
        return repository.find(tenant.trim(), id.trim())
                .orElseThrow(() -> new IllegalArgumentException("Warehouse work order not found"));
    }

    private void validate(WarehouseWorkOrder order, String lineId, BigDecimal quantity, WarehouseWorkType type) {
        if (order.type() != type) throw new IllegalStateException("Inventory operation does not match warehouse work type");
        if (order.status() != WarehouseWorkStatus.IN_PROGRESS)
            throw new IllegalStateException("Warehouse work must be IN_PROGRESS before inventory posting");
        if (lineId == null || lineId.isBlank() || quantity == null || quantity.signum() <= 0)
            throw new IllegalArgumentException("Line and positive quantity are required");
        WarehouseWorkLine line = order.lines().stream().filter(l -> l.lineId().equals(lineId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown warehouse work line: " + lineId));
        if (line.executedQuantity().add(quantity).compareTo(line.plannedQuantity()) > 0)
            throw new IllegalArgumentException("Execution exceeds remaining planned quantity");
    }

    private static String fingerprint(String... fields) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder canonical = new StringBuilder();
            for (String field : fields) {
                String value = field == null ? "" : field;
                canonical.append(value.length()).append(':').append(value);
            }
            return HexFormat.of().formatHex(digest.digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static String fingerprint(String operation, String tenant, String workOrderId, String lineId,
            String itemId, String warehouseId, Object quantity, Object unitCost, String currency) {
        return fingerprint(new String[]{operation, tenant, workOrderId, lineId, itemId, warehouseId,
                String.valueOf(quantity), String.valueOf(unitCost), currency});
    }

    private static String fingerprint(String operation, String tenant, String workOrderId, String lineId,
            String itemId, String warehouseId, String reservationId, Object quantity, String currency) {
        return fingerprint(new String[]{operation, tenant, workOrderId, lineId, itemId, warehouseId,
                reservationId, String.valueOf(quantity), currency});
    }

    private static String safeMessage(RuntimeException failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) return failure.getClass().getSimpleName();
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }

    @FunctionalInterface private interface InventoryAction { void run(); }
}
