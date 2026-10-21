package tech.kayys.numou.inventory.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.inventory.domain.inventory.*;
import tech.kayys.numou.inventory.spi.InventoryRepository;
import tech.kayys.numou.inventory.domain.reservation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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

        service.receive(itemId, whId, new BigDecimal("100"), new BigDecimal("10.00"), "USD", "REC-01");
        var bal1 = service.getStockBalance(itemId, whId);
        assertEquals(new BigDecimal("100"), bal1.onHand());
        assertEquals(new BigDecimal("1000.00"), bal1.totalValue());

        service.issue(itemId, whId, new BigDecimal("30"), "USD", "ISS-01");
        var bal2 = service.getStockBalance(itemId, whId);
        assertEquals(new BigDecimal("70"), bal2.onHand());

        assertThrows(IllegalStateException.class, () ->
                service.issue(itemId, whId, new BigDecimal("100"), "USD", "ISS-FAIL"));
    }

    @Test
    void inter_warehouse_transfer_posts_both_sides() {
        var service = new InventoryService(new TestInventoryRepository());
        var itemId = ItemId.generate();
        var wh1 = WarehouseId.generate();
        var wh2 = WarehouseId.generate();

        service.receive(itemId, wh1, new BigDecimal("50"), new BigDecimal("20.00"), "USD", "REC-1");
        service.transfer(itemId, wh1, wh2, new BigDecimal("20"), "USD", "TRF-1");

        assertEquals(new BigDecimal("30"), service.getStockBalance(itemId, wh1).onHand());
        assertEquals(new BigDecimal("20"), service.getStockBalance(itemId, wh2).onHand());
        assertEquals(2, service.getMovements(itemId).stream()
                .filter(m -> m.referenceNumber().startsWith("TRF-")).count());
    }

    @Test
    void insufficient_transfer_does_not_change_either_warehouse() {
        var service = new InventoryService(new TestInventoryRepository());
        var itemId = ItemId.generate();
        var source = WarehouseId.generate();
        var destination = WarehouseId.generate();
        service.receive(itemId, source, new BigDecimal("5"), new BigDecimal("4.00"), "USD", "REC-2");
        int movementCountBefore = service.getMovements(itemId).size();

        assertThrows(IllegalStateException.class, () ->
                service.transfer(itemId, source, destination, new BigDecimal("6"), "USD", "TRF-FAIL"));

        assertEquals(movementCountBefore, service.getMovements(itemId).size());
        assertEquals(new BigDecimal("5"), service.getStockBalance(itemId, source).onHand());
        assertEquals(BigDecimal.ZERO, service.getStockBalance(itemId, destination).onHand());
    }

    @Test
    void concurrent_issues_cannot_both_consume_the_final_unit() throws Exception {
        var repository = new TestInventoryRepository();
        var service = new InventoryService(repository);
        var itemId = ItemId.generate();
        var warehouseId = WarehouseId.generate();
        service.receive(itemId, warehouseId, BigDecimal.ONE, new BigDecimal("8.00"), "USD", "REC-RACE");

        var start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> tryIssue(service, itemId, warehouseId, "RACE-1", start));
            Future<Boolean> second = executor.submit(() -> tryIssue(service, itemId, warehouseId, "RACE-2", start));
            start.countDown();

            int successes = (first.get() ? 1 : 0) + (second.get() ? 1 : 0);
            assertEquals(1, successes);
            assertEquals(BigDecimal.ZERO, service.getStockBalance(itemId, warehouseId).onHand());
            assertEquals(2, service.getMovements(itemId).size());
        } finally {
            executor.shutdownNow();
        }
    }

    private static boolean tryIssue(InventoryService service, ItemId itemId, WarehouseId warehouseId,
                                    String reference, CountDownLatch start) throws InterruptedException {
        start.await();
        try {
            service.issue(itemId, warehouseId, BigDecimal.ONE, "USD", reference);
            return true;
        } catch (IllegalStateException insufficientStock) {
            return false;
        }
    }

    private static final class TestInventoryRepository implements InventoryRepository {
        private final List<InventoryMovement> movements = new ArrayList<>();

        @Override public void registerItem(InventoryItem item) {}
        @Override public void registerWarehouse(Warehouse warehouse) {}

        @Override
        public synchronized void saveMovement(InventoryMovement movement) {
            movements.add(movement);
        }

        @Override
        public synchronized void postIssueIfAvailable(InventoryMovement movement, BigDecimal requiredQuantity) {
            validateIssue(movement, requiredQuantity);
            BigDecimal onHand = onHand(movement.itemId(), movement.warehouseId());
            if (onHand.compareTo(requiredQuantity) < 0) {
                throw new IllegalStateException("Insufficient stock");
            }
            movements.add(movement);
        }

        @Override
        public synchronized void postTransferIfAvailable(InventoryMovement outbound, InventoryMovement inbound,
                                                         BigDecimal requiredQuantity) {
            validateIssue(outbound, requiredQuantity);
            if (inbound == null || inbound.type() != MovementType.RECEIPT
                    || !outbound.itemId().equals(inbound.itemId())
                    || outbound.warehouseId().equals(inbound.warehouseId())
                    || inbound.quantity().compareTo(requiredQuantity) != 0
                    || !outbound.currency().equals(inbound.currency())
                    || outbound.unitCost().compareTo(inbound.unitCost()) != 0) {
                throw new IllegalArgumentException("Invalid transfer movements");
            }
            if (onHand(outbound.itemId(), outbound.warehouseId()).compareTo(requiredQuantity) < 0) {
                throw new IllegalStateException("Insufficient stock");
            }
            movements.add(outbound);
            movements.add(inbound);
        }

        private static void validateIssue(InventoryMovement movement, BigDecimal requiredQuantity) {
            if (movement == null || movement.type() != MovementType.ISSUE
                    || requiredQuantity == null || requiredQuantity.signum() <= 0
                    || movement.quantity().compareTo(requiredQuantity.negate()) != 0) {
                throw new IllegalArgumentException("Invalid issue movement");
            }
        }

        private BigDecimal onHand(ItemId itemId, WarehouseId warehouseId) {
            return movements.stream()
                    .filter(m -> m.itemId().equals(itemId) && m.warehouseId().equals(warehouseId))
                    .map(InventoryMovement::quantity)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        @Override
        public synchronized List<InventoryReservation> findReservations(ItemId itemId, WarehouseId warehouseId) {
            return List.of();
        }

        @Override
        public synchronized List<InventoryMovement> findByItemAndWarehouse(ItemId itemId, WarehouseId warehouseId) {
            return movements.stream()
                    .filter(m -> m.itemId().equals(itemId) && m.warehouseId().equals(warehouseId))
                    .toList();
        }

        @Override
        public synchronized List<InventoryMovement> findByItem(ItemId itemId) {
            return movements.stream().filter(m -> m.itemId().equals(itemId)).toList();
        }
    }
}
