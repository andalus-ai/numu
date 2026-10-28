package tech.kayys.numou.inventory.adapter.memory;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.inventory.domain.inventory.*;

import java.math.BigDecimal;
import java.time.Instant;
import tech.kayys.numou.inventory.domain.reservation.InventoryReservation;
import tech.kayys.numou.inventory.domain.reservation.ReservationStatus;

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

    @Test
    void reservation_command_can_be_recovered_after_lost_response() {
        var repository = new InMemoryInventoryRepository();
        var item = ItemId.generate();
        var warehouse = WarehouseId.generate();
        repository.saveMovement(movement(item, warehouse, MovementType.RECEIPT, new BigDecimal("10"), "OPENING"));
        var key = new InventoryCommandKey("tenant-1", "checkout-line-1");
        var requested = InventoryReservation.create("tenant-1", item, warehouse,
                new BigDecimal("2"), "EA", "order-line-1", Instant.now().plusSeconds(300));

        var created = repository.reserveIfAvailable(key, requested);
        var recovered = repository.findReservationByCommandKey(key).orElseThrow();

        assertEquals(created.id(), recovered.id());
        assertEquals(ReservationStatus.ACTIVE, recovered.status());
        assertTrue(repository.findReservationByCommandKey(new InventoryCommandKey("tenant-1", "missing")).isEmpty());
    }

    @Test
    void reservation_command_key_replay_preserves_terminal_reservation_for_reconciliation() {
        var repository = new InMemoryInventoryRepository();
        var item = ItemId.generate();
        var warehouse = WarehouseId.generate();
        repository.saveMovement(movement(item, warehouse, MovementType.RECEIPT, new BigDecimal("10"), "OPENING"));
        var key = new InventoryCommandKey("tenant-1", "checkout-line-2");
        var requested = InventoryReservation.create("tenant-1", item, warehouse,
                new BigDecimal("2"), "EA", "order-line-2", Instant.now().plusSeconds(300));
        var created = repository.reserveIfAvailable(key, requested);
        repository.updateReservation(created.release());

        assertEquals(ReservationStatus.RELEASED, repository.findReservationByCommandKey(key).orElseThrow().status());
    }

    private static InventoryMovement movement(ItemId item, WarehouseId warehouse, MovementType type,
                                              BigDecimal quantity, String reference) {
        return new InventoryMovement(MovementId.generate(), item, warehouse, type, quantity,
                new BigDecimal("2.00"), "USD", reference, Instant.now());
    }
}
