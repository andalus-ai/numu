package tech.kayys.syirkah.inventory.application;

import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.inventory.domain.inventory.*;
import tech.kayys.syirkah.inventory.spi.InventoryRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InventoryServiceTest {

    @Test
    void receive_issue_and_balance_tracking() {
        var service = new InventoryService(new TestInventoryRepository());
        var itemId = ItemId.generate();
        var whId = WarehouseId.generate();

        service.registerItem(new InventoryItem(itemId, "SKU-01", "Widget",
                ValuationMethod.WEIGHTED_AVERAGE, TrackingMethod.NONE, "PCS"));
        service.registerWarehouse(new Warehouse(whId, "WH-MAIN", "Central Depot", "Jakarta"));

        // Receive 100 @ 10
        service.receive(itemId, whId, new BigDecimal("100"), new BigDecimal("10.00"), "USD", "REC-01");
        var bal1 = service.getStockBalance(itemId, whId);
        assertEquals(new BigDecimal("100"), bal1.onHand());
        assertEquals(new BigDecimal("1000.00"), bal1.totalValue());

        // Issue 30
        service.issue(itemId, whId, new BigDecimal("30"), "USD", "ISS-01");
        var bal2 = service.getStockBalance(itemId, whId);
        assertEquals(new BigDecimal("70"), bal2.onHand());

        // Attempt issue exceeding available -> throws
        assertThrows(IllegalStateException.class, () ->
                service.issue(itemId, whId, new BigDecimal("100"), "USD", "ISS-FAIL"));
    }

    @Test
    void inter_warehouse_transfer() {
        var service = new InventoryService(new TestInventoryRepository());
        var itemId = ItemId.generate();
        var wh1 = WarehouseId.generate();
        var wh2 = WarehouseId.generate();

        service.receive(itemId, wh1, new BigDecimal("50"), new BigDecimal("20.00"), "USD", "REC-1");
        service.transfer(itemId, wh1, wh2, new BigDecimal("20"), "USD", "TRF-1");

        assertEquals(new BigDecimal("30"), service.getStockBalance(itemId, wh1).onHand());
        assertEquals(new BigDecimal("20"), service.getStockBalance(itemId, wh2).onHand());
    }

    private static final class TestInventoryRepository implements InventoryRepository {
        private final List<InventoryMovement> movements = new ArrayList<>();

        @Override public void registerItem(InventoryItem item) {}
        @Override public void registerWarehouse(Warehouse warehouse) {}
        @Override public void saveMovement(InventoryMovement movement) { movements.add(movement); }
        @Override public List<InventoryMovement> findByItemAndWarehouse(ItemId itemId, WarehouseId warehouseId) {
            return movements.stream()
                    .filter(movement -> movement.itemId().equals(itemId)
                            && movement.warehouseId().equals(warehouseId))
                    .toList();
        }
        @Override public List<InventoryMovement> findByItem(ItemId itemId) {
            return movements.stream().filter(movement -> movement.itemId().equals(itemId)).toList();
        }
    }
}
