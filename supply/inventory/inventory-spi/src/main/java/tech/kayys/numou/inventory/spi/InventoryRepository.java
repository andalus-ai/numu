package tech.kayys.numou.inventory.spi;

import tech.kayys.numou.inventory.domain.inventory.*;
import tech.kayys.numou.inventory.domain.reservation.*;

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

    /** Persist once; a replay with matching business payload returns the original movement. */
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

    /**
     * Atomically appends an issue if unreserved available quantity is sufficient.
     * The movement quantity must be negative and {@code requiredQuantity} positive.
     *
     * @throws IllegalStateException when stock is insufficient
     */
    void postIssueIfAvailable(InventoryMovement movement, BigDecimal requiredQuantity);

    /**
     * Atomically appends both sides of a transfer if unreserved source availability is sufficient.
     * Neither movement may be persisted unless both can be persisted.
     */
    void postTransferIfAvailable(InventoryMovement outbound, InventoryMovement inbound,
                                 BigDecimal requiredQuantity);

    /** Atomically creates an ACTIVE reservation only when unreserved stock covers its quantity. */
    default InventoryReservation reserveIfAvailable(InventoryReservation reservation) {
        throw new UnsupportedOperationException("This adapter does not implement reservations");
    }
    default InventoryReservation findReservation(ReservationId id) {
        throw new UnsupportedOperationException("This adapter does not implement reservations");
    }
    /** Persists an ACTIVE-to-RELEASED/EXPIRED transition using optimistic version checks. */
    default InventoryReservation updateReservation(InventoryReservation reservation) {
        throw new UnsupportedOperationException("This adapter does not implement reservations");
    }
    default List<InventoryReservation> findReservations(ItemId itemId, WarehouseId warehouseId) {
        throw new UnsupportedOperationException("This adapter does not implement reservations");
    }
    /** Atomically posts a full reserved issue and transitions its reservation to COMMITTED. */
    default InventoryMovement postReservedIssue(ReservationId reservationId, InventoryMovement movement,
                                                BigDecimal requiredQuantity) {
        throw new UnsupportedOperationException("This adapter does not implement reserved issues");
    }

    List<InventoryMovement> findByItemAndWarehouse(ItemId itemId, WarehouseId warehouseId);
    List<InventoryMovement> findByItem(ItemId itemId);
}
