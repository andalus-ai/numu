package tech.kayys.numou.inventory.adapter.memory;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.inventory.domain.inventory.*;
import tech.kayys.numou.inventory.domain.reservation.*;
import java.math.BigDecimal;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

class InventoryReservationAvailabilityTest {
    @Test
    void reservations_reduce_available_stock_and_prevent_unreserved_issue() {
        var repo = new InMemoryInventoryRepository();
        var item = ItemId.generate(); var warehouse = WarehouseId.generate();
        repo.saveMovement(movement(item, warehouse, MovementType.RECEIPT, "10", "OPENING"));
        var reservation = InventoryReservation.create("tenant-a", item, warehouse,
                new BigDecimal("7"), "EA", "ORDER-1", null);
        repo.reserveIfAvailable(reservation);

        assertEquals(new BigDecimal("10"), balance(repo, item, warehouse).onHand());
        assertEquals(new BigDecimal("7"), balance(repo, item, warehouse).reserved());
        assertEquals(new BigDecimal("3"), balance(repo, item, warehouse).available());
        assertThrows(IllegalStateException.class, () -> repo.postIssueIfAvailable(
                movement(item, warehouse, MovementType.ISSUE, "-4", "UNRESERVED"), new BigDecimal("4")));
    }

    @Test
    void reserved_issue_posts_movement_and_commits_reservation_atomically() {
        var repo = new InMemoryInventoryRepository();
        var item = ItemId.generate(); var warehouse = WarehouseId.generate();
        repo.saveMovement(movement(item, warehouse, MovementType.RECEIPT, "10", "OPENING"));
        var reservation = repo.reserveIfAvailable(InventoryReservation.create("tenant-a", item, warehouse,
                new BigDecimal("4"), "EA", "ORDER-2", null));
        var issue = movement(item, warehouse, MovementType.ISSUE, "-4", "FULFILL-2");

        repo.postReservedIssue(reservation.id(), issue, new BigDecimal("4"));

        assertEquals(ReservationStatus.COMMITTED, repo.findReservation(reservation.id()).status());
        assertEquals(new BigDecimal("6"), balance(repo, item, warehouse).onHand());
        assertEquals(BigDecimal.ZERO, balance(repo, item, warehouse).reserved());
    }

    @Test
    void reservation_cannot_overbook_available_stock() {
        var repo = new InMemoryInventoryRepository();
        var item = ItemId.generate(); var warehouse = WarehouseId.generate();
        repo.saveMovement(movement(item, warehouse, MovementType.RECEIPT, "5", "OPENING"));
        repo.reserveIfAvailable(InventoryReservation.create("tenant-a", item, warehouse,
                new BigDecimal("4"), "EA", "ORDER-3", null));
        assertThrows(IllegalStateException.class, () -> repo.reserveIfAvailable(
                InventoryReservation.create("tenant-b", item, warehouse, new BigDecimal("2"), "EA", "ORDER-4", null)));
    }

    @Test
    void releasing_reservation_restores_availability_and_stale_transition_is_rejected() {
        var repo = new InMemoryInventoryRepository();
        var item = ItemId.generate(); var warehouse = WarehouseId.generate();
        repo.saveMovement(movement(item, warehouse, MovementType.RECEIPT, "5", "OPENING"));
        var reservation = repo.reserveIfAvailable(InventoryReservation.create("tenant-a", item, warehouse,
                new BigDecimal("4"), "EA", "ORDER-RELEASE", null));
        var released = repo.updateReservation(reservation.release());
        assertEquals(ReservationStatus.RELEASED, released.status());
        assertEquals(new BigDecimal("5"), balance(repo, item, warehouse).available());
        assertThrows(IllegalStateException.class, () -> repo.updateReservation(reservation.release()));
    }

    @Test
    void concurrent_reservations_cannot_overbook_stock() throws Exception {
        var repo = new InMemoryInventoryRepository();
        var item = ItemId.generate(); var warehouse = WarehouseId.generate();
        repo.saveMovement(movement(item, warehouse, MovementType.RECEIPT, "5", "OPENING"));
        var start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> a = executor.submit(() -> tryReserve(repo, item, warehouse, "ORDER-A", start));
            Future<Boolean> b = executor.submit(() -> tryReserve(repo, item, warehouse, "ORDER-B", start));
            start.countDown();
            assertEquals(1, (a.get() ? 1 : 0) + (b.get() ? 1 : 0));
            assertEquals(new BigDecimal("4"), balance(repo, item, warehouse).reserved());
        } finally {
            executor.shutdownNow();
        }
    }

    private static boolean tryReserve(InMemoryInventoryRepository repo, ItemId item, WarehouseId warehouse,
                                      String reference, CountDownLatch start) throws InterruptedException {
        start.await();
        try {
            repo.reserveIfAvailable(InventoryReservation.create("tenant-a", item, warehouse,
                    new BigDecimal("4"), "EA", reference, null));
            return true;
        } catch (IllegalStateException insufficient) {
            return false;
        }
    }

    private static StockBalance balance(InMemoryInventoryRepository repo, ItemId item, WarehouseId warehouse) {
        var movements = repo.findByItemAndWarehouse(item, warehouse);
        BigDecimal onHand = movements.stream().map(InventoryMovement::quantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal reserved = repo.findReservations(item, warehouse).stream()
                .filter(r -> r.status() == ReservationStatus.ACTIVE)
                .filter(r -> r.expiresAt() == null || r.expiresAt().isAfter(Instant.now()))
                .map(InventoryReservation::quantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new StockBalance(item, warehouse, onHand, reserved, onHand.subtract(reserved).max(BigDecimal.ZERO), BigDecimal.ZERO);
    }

    private static InventoryMovement movement(ItemId item, WarehouseId warehouse, MovementType type, String quantity, String ref) {
        return new InventoryMovement(MovementId.generate(), item, warehouse, type, new BigDecimal(quantity),
                new BigDecimal("1"), "USD", ref, Instant.now());
    }
}
