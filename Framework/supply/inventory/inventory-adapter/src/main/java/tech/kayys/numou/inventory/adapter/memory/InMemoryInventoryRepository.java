package tech.kayys.syirkah.inventory.adapter.memory;

import tech.kayys.syirkah.inventory.domain.inventory.*;
import tech.kayys.syirkah.inventory.spi.InventoryRepository;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class InMemoryInventoryRepository implements InventoryRepository {
    private final ConcurrentHashMap<ItemId, InventoryItem> items = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<WarehouseId, Warehouse> warehouses = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<InventoryMovement> movements = new CopyOnWriteArrayList<>();

    @Override
    public void registerItem(InventoryItem item) {
        items.put(item.id(), item);
    }

    @Override
    public void registerWarehouse(Warehouse warehouse) {
        warehouses.put(warehouse.id(), warehouse);
    }

    @Override
    public void saveMovement(InventoryMovement movement) {
        movements.add(movement);
    }

    @Override
    public List<InventoryMovement> findByItemAndWarehouse(ItemId itemId, WarehouseId warehouseId) {
        return movements.stream()
                .filter(movement -> movement.itemId().equals(itemId)
                        && movement.warehouseId().equals(warehouseId))
                .toList();
    }

    @Override
    public List<InventoryMovement> findByItem(ItemId itemId) {
        return movements.stream()
                .filter(movement -> movement.itemId().equals(itemId))
                .toList();
    }
}
