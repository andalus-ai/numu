package tech.kayys.numou.automotiveparts.purchasing;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.inventory.adapter.memory.InMemoryInventoryRepository;
import tech.kayys.numou.inventory.application.InventoryService;
import tech.kayys.numou.inventory.domain.inventory.*;
import tech.kayys.numou.purchasing.domain.procurement.GoodsReceipt;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GoodsReceiptInventoryPostingServiceTest {
    @Test
    void retryPostsOneInventoryMovementAndConvertsReceiptUnits() {
        var repository = new InMemoryInventoryRepository();
        var inventory = new InventoryService(repository);
        var itemId = ItemId.of("part-filter-01");
        var warehouseId = WarehouseId.of("warehouse-bandung");
        inventory.registerItem(new InventoryItem(itemId, "FILTER-01", "Oil filter",
                ValuationMethod.WEIGHTED_AVERAGE, TrackingMethod.NONE, "EA"));
        inventory.registerWarehouse(new Warehouse(warehouseId, "BDG-01", "Bandung parts store", "Bandung"));

        List<String> mappingCalls = new ArrayList<>();
        var service = new GoodsReceiptInventoryPostingService(inventory, (tenant, po, line) -> {
            mappingCalls.add(tenant + ":" + po + ":" + line);
            return new GoodsReceiptInventoryPostingService.ReceiptInventoryTarget(itemId, warehouseId,
                    new BigDecimal("12"), new BigDecimal("25000"), "IDR");
        });
        var receipt = receipt("tenant-a", "receipt-100", "12");

        var first = service.post(receipt);
        var replay = service.post(receipt);

        assertEquals(first.id(), replay.id());
        assertEquals(new BigDecimal("144"), inventory.getStockBalance(itemId, warehouseId).onHand());
        assertEquals(1, inventory.getMovements(itemId).size());
        assertEquals(List.of("tenant-a:PO-100:PO-100-L1", "tenant-a:PO-100:PO-100-L1"), mappingCalls);
        assertEquals("goods-receipt:receipt-100", replay.referenceNumber());
    }

    @Test
    void reusingReceiptIdentityWithChangedQuantityIsRejected() {
        var repository = new InMemoryInventoryRepository();
        var inventory = new InventoryService(repository);
        var itemId = ItemId.of("part-filter-01");
        var warehouseId = WarehouseId.of("warehouse-bandung");
        var service = new GoodsReceiptInventoryPostingService(inventory, (tenant, po, line) ->
                new GoodsReceiptInventoryPostingService.ReceiptInventoryTarget(itemId, warehouseId,
                        BigDecimal.ONE, new BigDecimal("25000"), "IDR"));

        service.post(receipt("tenant-a", "receipt-101", "2"));
        assertThrows(IllegalArgumentException.class,
                () -> service.post(receipt("tenant-a", "receipt-101", "3")));
        assertEquals(1, inventory.getMovements(itemId).size());
    }

    @Test
    void sameReceiptIdInDifferentTenantsUsesDifferentIdempotencyScope() {
        var repository = new InMemoryInventoryRepository();
        var inventory = new InventoryService(repository);
        var itemId = ItemId.of("part-filter-01");
        var warehouseId = WarehouseId.of("warehouse-bandung");
        var service = new GoodsReceiptInventoryPostingService(inventory, (tenant, po, line) ->
                new GoodsReceiptInventoryPostingService.ReceiptInventoryTarget(itemId, warehouseId,
                        BigDecimal.ONE, new BigDecimal("25000"), "IDR"));

        service.post(receipt("tenant-a", "receipt-shared", "2"));
        service.post(receipt("tenant-b", "receipt-shared", "2"));

        assertEquals(new BigDecimal("4"), inventory.getStockBalance(itemId, warehouseId).onHand());
        assertEquals(2, inventory.getMovements(itemId).size());
    }

    private static GoodsReceipt receipt(String tenant, String id, String quantity) {
        return new GoodsReceipt(id, tenant, "ledger-" + tenant, "PO-100", "PO-100-L1",
                new BigDecimal(quantity), "receiver-1");
    }
}
