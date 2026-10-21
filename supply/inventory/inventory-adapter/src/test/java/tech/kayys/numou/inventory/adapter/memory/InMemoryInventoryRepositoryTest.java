package tech.kayys.numou.inventory.adapter.memory;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.inventory.domain.inventory.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryInventoryRepositoryTest {

    @Test
    void concurrent_conditional_issues_do_not_overdraw_stock() throws Exception {
        var repository = new InMemoryInventoryRepository();
        var itemId = ItemId.generate();
        var warehouseId = WarehouseId.generate();
        repository.saveMovement(movement(itemId, warehouseId, MovementType.RECEIPT,
                BigDecimal.ONE, "REC-1"));

        var start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> tryIssue(repository, itemId, warehouseId, "ISS-1", start));
            Future<Boolean> second = executor.submit(() -> tryIssue(repository, itemId, warehouseId, "ISS-2", start));
            start.countDown();

            assertEquals(1, (first.get() ? 1 : 0) + (second.get() ? 1 : 0));
            assertEquals(2, repository.findByItemAndWarehouse(itemId, warehouseId).size());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void failed_transfer_persists_neither_movement() {
        var repository = new InMemoryInventoryRepository();
        var itemId = ItemId.generate();
        var source = WarehouseId.generate();
        var destination = WarehouseId.generate();
        repository.saveMovement(movement(itemId, source, MovementType.RECEIPT,
                new BigDecimal("2"), "REC-2"));

        var outbound = movement(itemId, source, MovementType.ISSUE,
                new BigDecimal("-3"), "TRF-OUT");
        var inbound = movement(itemId, destination, MovementType.RECEIPT,
                new BigDecimal("3"), "TRF-IN");

        assertThrows(IllegalStateException.class,
                () -> repository.postTransferIfAvailable(outbound, inbound, new BigDecimal("3")));
        assertEquals(1, repository.findByItem(itemId).size());
        assertEquals(new BigDecimal("2"), repository.findByItemAndWarehouse(itemId, source).stream()
                .map(InventoryMovement::quantity).reduce(BigDecimal.ZERO, BigDecimal::add));
        assertTrue(repository.findByItemAndWarehouse(itemId, destination).isEmpty());
    }

    private static boolean tryIssue(InMemoryInventoryRepository repository, ItemId itemId,
                                    WarehouseId warehouseId, String reference, CountDownLatch start)
            throws InterruptedException {
        start.await();
        try {
            repository.postIssueIfAvailable(
                    movement(itemId, warehouseId, MovementType.ISSUE, BigDecimal.ONE.negate(), reference),
                    BigDecimal.ONE);
            return true;
        } catch (IllegalStateException insufficientStock) {
            return false;
        }
    }

    private static InventoryMovement movement(ItemId itemId, WarehouseId warehouseId,
                                               MovementType type, BigDecimal quantity, String reference) {
        return new InventoryMovement(MovementId.generate(), itemId, warehouseId, type,
                quantity, new BigDecimal("5.00"), "USD", reference, Instant.now());
    }
}
