package tech.kayys.numou.inventory.adapter.memory;

import tech.kayys.numou.inventory.domain.inventory.*;
import tech.kayys.numou.inventory.spi.InventoryRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single-process reference adapter. Synchronization here does not provide
 * cross-process or durable database transaction guarantees.
 */
public final class InMemoryInventoryRepository implements InventoryRepository {
    private final ConcurrentHashMap<ItemId, InventoryItem> items = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<WarehouseId, Warehouse> warehouses = new ConcurrentHashMap<>();
    private final List<InventoryMovement> movements = new ArrayList<>();
    private final Map<InventoryCommandKey, List<InventoryMovement>> commandMovements = new HashMap<>();

    @Override
    public void registerItem(InventoryItem item) {
        items.put(item.id(), item);
    }

    @Override
    public void registerWarehouse(Warehouse warehouse) {
        warehouses.put(warehouse.id(), warehouse);
    }

    @Override
    public synchronized void saveMovement(InventoryMovement movement) {
        movements.add(movement);
    }


    @Override
    public synchronized InventoryMovement saveMovementIdempotently(InventoryCommandKey key, InventoryMovement movement) {
        List<InventoryMovement> existing = replayOrNull(key, List.of(movement));
        if (existing != null) return existing.get(0);
        movements.add(movement); commandMovements.put(key, List.of(movement)); return movement;
    }

    @Override
    public synchronized InventoryMovement postIssueIfAvailable(InventoryCommandKey key, InventoryMovement movement, BigDecimal quantity) {
        List<InventoryMovement> existing = replayOrNull(key, List.of(movement));
        if (existing != null) return existing.get(0);
        postIssueIfAvailable(movement, quantity); commandMovements.put(key, List.of(movement)); return movement;
    }

    @Override
    public synchronized List<InventoryMovement> postTransferIfAvailable(InventoryCommandKey key, InventoryMovement outbound,
                                                                        InventoryMovement inbound, BigDecimal quantity) {
        List<InventoryMovement> requested = List.of(outbound, inbound);
        List<InventoryMovement> existing = replayOrNull(key, requested);
        if (existing != null) return existing;
        postTransferIfAvailable(outbound, inbound, quantity); commandMovements.put(key, requested); return requested;
    }

    private List<InventoryMovement> replayOrNull(InventoryCommandKey key, List<InventoryMovement> requested) {
        if (key == null) throw new IllegalArgumentException("Command key is required");
        List<InventoryMovement> existing = commandMovements.get(key);
        if (existing == null) return null;
        if (existing.size() != requested.size()) throw new IllegalArgumentException("Idempotency key payload mismatch");
        for (int i = 0; i < existing.size(); i++) {
            InventoryMovement a = existing.get(i), b = requested.get(i);
            if (!a.itemId().equals(b.itemId()) || !a.warehouseId().equals(b.warehouseId())
                    || a.type() != b.type() || a.quantity().compareTo(b.quantity()) != 0
                    || !a.currency().equals(b.currency())
                    || !java.util.Objects.equals(a.referenceNumber(), b.referenceNumber()))
                throw new IllegalArgumentException("Idempotency key payload mismatch");
        }
        return existing;
    }

    @Override
    public synchronized void postIssueIfAvailable(InventoryMovement movement, BigDecimal requiredQuantity) {
        validateIssue(movement, requiredQuantity);
        BigDecimal onHand = onHand(movement.itemId(), movement.warehouseId());
        if (onHand.compareTo(requiredQuantity) < 0) {
            throw new IllegalStateException("Insufficient stock: available=" + onHand
                    + ", requested=" + requiredQuantity);
        }
        movements.add(movement);
    }

    @Override
    public synchronized void postTransferIfAvailable(InventoryMovement outbound,
                                                     InventoryMovement inbound,
                                                     BigDecimal requiredQuantity) {
        validateIssue(outbound, requiredQuantity);
        if (inbound == null || inbound.type() != MovementType.RECEIPT) {
            throw new IllegalArgumentException("Inbound transfer movement must be a receipt");
        }
        if (!outbound.itemId().equals(inbound.itemId())) {
            throw new IllegalArgumentException("Transfer movements must reference the same item");
        }
        if (outbound.warehouseId().equals(inbound.warehouseId())) {
            throw new IllegalArgumentException("Transfer source and destination must differ");
        }
        if (inbound.quantity().compareTo(requiredQuantity) != 0) {
            throw new IllegalArgumentException("Inbound quantity must equal transfer quantity");
        }
        if (!outbound.currency().equals(inbound.currency())
                || outbound.unitCost().compareTo(inbound.unitCost()) != 0) {
            throw new IllegalArgumentException("Transfer valuation and currencies must match");
        }
        BigDecimal onHand = onHand(outbound.itemId(), outbound.warehouseId());
        if (onHand.compareTo(requiredQuantity) < 0) {
            throw new IllegalStateException("Insufficient stock for transfer: available=" + onHand
                    + ", requested=" + requiredQuantity);
        }

        // Both movements are appended while holding the same lock used by reads and issues.
        movements.add(outbound);
        movements.add(inbound);
    }

    private static void validateIssue(InventoryMovement movement, BigDecimal requiredQuantity) {
        if (movement == null || movement.type() != MovementType.ISSUE
                || requiredQuantity == null || requiredQuantity.signum() <= 0) {
            throw new IllegalArgumentException("Issue movement and positive required quantity are required");
        }
        if (movement.quantity().compareTo(requiredQuantity.negate()) != 0) {
            throw new IllegalArgumentException("Issue movement quantity must be the negative requested quantity");
        }
    }

    private BigDecimal onHand(ItemId itemId, WarehouseId warehouseId) {
        return movements.stream()
                .filter(movement -> movement.itemId().equals(itemId)
                        && movement.warehouseId().equals(warehouseId))
                .map(InventoryMovement::quantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public synchronized List<InventoryMovement> findByItemAndWarehouse(ItemId itemId, WarehouseId warehouseId) {
        return movements.stream()
                .filter(movement -> movement.itemId().equals(itemId)
                        && movement.warehouseId().equals(warehouseId))
                .toList();
    }

    @Override
    public synchronized List<InventoryMovement> findByItem(ItemId itemId) {
        return movements.stream()
                .filter(movement -> movement.itemId().equals(itemId))
                .toList();
    }
}
