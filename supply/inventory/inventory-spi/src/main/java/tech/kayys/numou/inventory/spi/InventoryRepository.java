package tech.kayys.numou.inventory.spi;

import tech.kayys.numou.inventory.domain.inventory.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Persistence boundary for the inventory ledger.
 *
 * <p>Implementations must make conditional issues and transfers atomic across
 * all participating ledger entries. Database-backed adapters must use a
 * transaction/lock or atomic conditional update; synchronizing one JVM is
 * insufficient for multi-instance deployments.</p>
 */
public interface InventoryRepository {
    void registerItem(InventoryItem item);
    void registerWarehouse(Warehouse warehouse);
    void saveMovement(InventoryMovement movement);

    /**
     * Atomically appends an issue if the current on-hand quantity is sufficient.
     * The movement quantity must be negative and {@code requiredQuantity} positive.
     *
     * @throws IllegalStateException when stock is insufficient
     */
    void postIssueIfAvailable(InventoryMovement movement, BigDecimal requiredQuantity);

    /**
     * Atomically appends both sides of a transfer if source stock is sufficient.
     * Neither movement may be persisted unless both can be persisted.
     */
    void postTransferIfAvailable(InventoryMovement outbound, InventoryMovement inbound,
                                 BigDecimal requiredQuantity);

    List<InventoryMovement> findByItemAndWarehouse(ItemId itemId, WarehouseId warehouseId);
    List<InventoryMovement> findByItem(ItemId itemId);
}
