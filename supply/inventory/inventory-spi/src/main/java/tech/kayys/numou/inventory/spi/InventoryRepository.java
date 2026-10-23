package tech.kayys.numou.inventory.spi;

import tech.kayys.numou.inventory.domain.inventory.*;
import tech.kayys.numou.inventory.domain.reservation.*;
import java.math.BigDecimal;
import java.util.List;

/**
 * Persistence boundary for the inventory ledger.
 * Implementations must enforce tenant scope and make conditional stock operations atomic.
 * Multi-instance adapters must use database transactions, row locks, or conditional writes.
 */
public interface InventoryRepository {
    void registerItem(InventoryItem item);
    void registerWarehouse(Warehouse warehouse);
    void saveMovement(InventoryMovement movement);
    default InventoryMovement saveMovementIdempotently(InventoryCommandKey key, InventoryMovement movement) {
        throw new UnsupportedOperationException("This adapter does not implement durable command idempotency");
    }
    default InventoryMovement postIssueIfAvailable(InventoryCommandKey key, InventoryMovement movement, BigDecimal requiredQuantity) {
        throw new UnsupportedOperationException("This adapter does not implement durable command idempotency");
    }
    default List<InventoryMovement> postTransferIfAvailable(InventoryCommandKey key, InventoryMovement outbound,
                                                            InventoryMovement inbound, BigDecimal requiredQuantity) {
        throw new UnsupportedOperationException("This adapter does not implement durable command idempotency");
    }
    void postIssueIfAvailable(InventoryMovement movement, BigDecimal requiredQuantity);
    void postTransferIfAvailable(InventoryMovement outbound, InventoryMovement inbound, BigDecimal requiredQuantity);
    default InventoryReservation reserveIfAvailable(InventoryReservation reservation) {
        throw new UnsupportedOperationException("This adapter does not implement reservations");
    }
    default InventoryReservation findReservation(ReservationId id) {
        throw new UnsupportedOperationException("This adapter does not implement reservations");
    }
    default InventoryReservation updateReservation(InventoryReservation reservation) {
        throw new UnsupportedOperationException("This adapter does not implement reservations");
    }
    default List<InventoryReservation> findReservations(ItemId itemId, WarehouseId warehouseId) {
        throw new UnsupportedOperationException("This adapter does not implement reservations");
    }
    /** Atomically posts a reserved issue and transitions the reservation; implementations must reject duplicate/conflicting keys. */
    default InventoryMovement postReservedIssue(InventoryCommandKey key, ReservationId reservationId,
                                                InventoryMovement movement, BigDecimal requiredQuantity) {
        throw new UnsupportedOperationException("This adapter does not implement idempotent reserved issues");
    }
    /** Legacy non-idempotent operation retained for compatibility. */
    default InventoryMovement postReservedIssue(ReservationId reservationId, InventoryMovement movement,
                                                BigDecimal requiredQuantity) {
        throw new UnsupportedOperationException("This adapter does not implement reserved issues");
    }
    List<InventoryMovement> findByItemAndWarehouse(ItemId itemId, WarehouseId warehouseId);
    List<InventoryMovement> findByItem(ItemId itemId);
}