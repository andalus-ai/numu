package tech.kayys.syirkah.inventory.spi;

import tech.kayys.syirkah.inventory.domain.inventory.*;

import java.util.List;

public interface InventoryRepository {
    void registerItem(InventoryItem item);
    void registerWarehouse(Warehouse warehouse);
    void saveMovement(InventoryMovement movement);
    List<InventoryMovement> findByItemAndWarehouse(ItemId itemId, WarehouseId warehouseId);
    List<InventoryMovement> findByItem(ItemId itemId);
}
