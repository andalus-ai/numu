package tech.kayys.numou.inventory.adapter.memory;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.inventory.domain.inventory.*;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class InventoryCommandIdempotencyTest {
    @Test
    void receipt_replay_returns_original_movement_without_duplicate_posting() {
        var repository = new InMemoryInventoryRepository();
        var item = ItemId.generate();
        var warehouse = WarehouseId.generate();
        var key = new InventoryCommandKey("tenant-1", "receipt-123");
        var first = movement(item, warehouse, MovementType.RECEIPT, new BigDecimal("5"), "RCV-123");
        var replay = movement(item, warehouse, MovementType.RECEIPT, new BigDecimal("5"), "RCV-123");

        var posted = repository.saveMovementIdempotently(key, first);
        var replayed = repository.saveMovementIdempotently(key, replay);

        assertEquals(posted.id(), replayed.id());
        assertEquals(1, repository.findByItem(item).size());
    }

    @Test
    void same_key_with_changed_quantity_is_rejected() {
        var repository = new InMemoryInventoryRepository();
        var item = ItemId.generate();
        var warehouse = WarehouseId.generate();
        var key = new InventoryCommandKey("tenant-1", "receipt-124");
        repository.saveMovementIdempotently(key,
                movement(item, warehouse, MovementType.RECEIPT, new BigDecimal("5"), "RCV-124"));

        assertThrows(IllegalArgumentException.class, () -> repository.saveMovementIdempotently(key,
                movement(item, warehouse, MovementType.RECEIPT, new BigDecimal("6"), "RCV-124")));
        assertEquals(1, repository.findByItem(item).size());
    }

    @Test
    void transfer_replay_does_not_append_second_pair() {
        var repository = new InMemoryInventoryRepository();
        var item = ItemId.generate();
        var from = WarehouseId.generate();
        var to = WarehouseId.generate();
        repository.saveMovement(movement(item, from, MovementType.RECEIPT, new BigDecimal("8"), "OPENING"));
        var key = new InventoryCommandKey("tenant-1", "transfer-125");
        var outbound = movement(item, from, MovementType.ISSUE, new BigDecimal("-3"), "TRF-125-OUT");
        var inbound = movement(item, to, MovementType.RECEIPT, new BigDecimal("3"), "TRF-125-IN");
        repository.postTransferIfAvailable(key, outbound, inbound, new BigDecimal("3"));

        var replayOutbound = movement(item, from, MovementType.ISSUE, new BigDecimal("-3"), "TRF-125-OUT");
        var replayInbound = movement(item, to, MovementType.RECEIPT, new BigDecimal("3"), "TRF-125-IN");
        var replay = repository.postTransferIfAvailable(key, replayOutbound, replayInbound, new BigDecimal("3"));

        assertEquals(outbound.id(), replay.get(0).id());
        assertEquals(inbound.id(), replay.get(1).id());
        assertEquals(3, repository.findByItem(item).size());
    }

    private static InventoryMovement movement(ItemId item, WarehouseId warehouse, MovementType type,
                                              BigDecimal quantity, String reference) {
        return new InventoryMovement(MovementId.generate(), item, warehouse, type, quantity,
                new BigDecimal("2.00"), "USD", reference, Instant.now());
    }
}
