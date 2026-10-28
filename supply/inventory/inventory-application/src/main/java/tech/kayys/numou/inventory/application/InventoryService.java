package tech.kayys.numou.inventory.application;

import tech.kayys.numou.inventory.domain.inventory.*;
import tech.kayys.numou.inventory.spi.InventoryRepository;
import tech.kayys.numou.inventory.domain.reservation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Application service managing inventory ledger movements, balances, and transfers.
 */
public final class InventoryService {

    private final InventoryRepository repository;

    public InventoryService(InventoryRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    public void registerItem(InventoryItem item) { repository.registerItem(item); }
    public void registerWarehouse(Warehouse wh) { repository.registerWarehouse(wh); }

    /** Idempotent receipt; key must remain stable across retries. */
    public InventoryMovement receive(InventoryCommandKey key, ItemId itemId, WarehouseId warehouseId,
                                     BigDecimal quantity, BigDecimal unitCost, String currency, String ref) {
        Objects.requireNonNull(key, "Command key is required"); requirePositive(quantity, "Received quantity");
        return repository.saveMovementIdempotently(key, new InventoryMovement(MovementId.generate(), itemId,
                warehouseId, MovementType.RECEIPT, quantity, unitCost, currency, ref, Instant.now()));
    }

    /** Inward goods movement: increases stock on-hand. */
    public InventoryMovement receive(ItemId itemId, WarehouseId warehouseId,
                                     BigDecimal quantity, BigDecimal unitCost,
                                     String currency, String ref) {
        requirePositive(quantity, "Received quantity");
        InventoryMovement movement = new InventoryMovement(
                MovementId.generate(), itemId, warehouseId, MovementType.RECEIPT,
                quantity, unitCost, currency, ref, Instant.now());
        repository.saveMovement(movement);
        return movement;
    }

    /** Outward movement: atomically checks on-hand stock and appends the issue. */
    public InventoryMovement issue(ItemId itemId, WarehouseId warehouseId,
                                   BigDecimal quantity, String currency, String ref) {
        requirePositive(quantity, "Issued quantity");
        StockBalance current = getStockBalance(itemId, warehouseId);
        BigDecimal unitCost = current.onHand().signum() > 0
                ? current.totalValue().divide(current.onHand(), 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        InventoryMovement movement = new InventoryMovement(
                MovementId.generate(), itemId, warehouseId, MovementType.ISSUE,
                quantity.negate(), unitCost, currency, ref, Instant.now());
        repository.postIssueIfAvailable(movement, quantity);
        return movement;
    }

    /** Idempotent issue; key must remain stable across retries. */
    public InventoryMovement issue(InventoryCommandKey key, ItemId itemId, WarehouseId warehouseId,
                                   BigDecimal quantity, String currency, String ref) {
        Objects.requireNonNull(key, "Command key is required"); requirePositive(quantity, "Issued quantity");
        StockBalance current = getStockBalance(itemId, warehouseId);
        BigDecimal unitCost = current.onHand().signum() > 0
                ? current.totalValue().divide(current.onHand(), 4, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        return repository.postIssueIfAvailable(key, new InventoryMovement(MovementId.generate(), itemId,
                warehouseId, MovementType.ISSUE, quantity.negate(), unitCost, currency, ref, Instant.now()), quantity);
    }

    /** Atomically posts both sides of an inter-warehouse stock transfer. */
    public void transfer(ItemId itemId, WarehouseId fromWarehouse, WarehouseId toWarehouse,
                         BigDecimal quantity, String currency, String ref) {
        requirePositive(quantity, "Transfer quantity");
        Objects.requireNonNull(fromWarehouse, "Source warehouse is required");
        Objects.requireNonNull(toWarehouse, "Destination warehouse is required");
        if (fromWarehouse.equals(toWarehouse)) {
            throw new IllegalArgumentException("Transfer source and destination must differ");
        }

        StockBalance source = getStockBalance(itemId, fromWarehouse);
        BigDecimal unitCost = source.onHand().signum() > 0
                ? source.totalValue().divide(source.onHand(), 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        Instant now = Instant.now();
        InventoryMovement outbound = new InventoryMovement(
                MovementId.generate(), itemId, fromWarehouse, MovementType.ISSUE,
                quantity.negate(), unitCost, currency, ref + "-OUT", now);
        InventoryMovement inbound = new InventoryMovement(
                MovementId.generate(), itemId, toWarehouse, MovementType.RECEIPT,
                quantity, unitCost, currency, ref + "-IN", now);
        repository.postTransferIfAvailable(outbound, inbound, quantity);
    }

    /** Idempotent transfer; replay returns the original movement pair. */
    public void transfer(InventoryCommandKey key, ItemId itemId, WarehouseId fromWarehouse, WarehouseId toWarehouse,
                         BigDecimal quantity, String currency, String ref) {
        Objects.requireNonNull(key, "Command key is required"); requirePositive(quantity, "Transfer quantity");
        Objects.requireNonNull(fromWarehouse, "Source warehouse is required");
        Objects.requireNonNull(toWarehouse, "Destination warehouse is required");
        if (fromWarehouse.equals(toWarehouse)) throw new IllegalArgumentException("Transfer source and destination must differ");
        StockBalance source = getStockBalance(itemId, fromWarehouse);
        BigDecimal unitCost = source.onHand().signum() > 0
                ? source.totalValue().divide(source.onHand(), 4, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        Instant now = Instant.now();
        InventoryMovement outbound = new InventoryMovement(MovementId.generate(), itemId, fromWarehouse,
                MovementType.ISSUE, quantity.negate(), unitCost, currency, ref + "-OUT", now);
        InventoryMovement inbound = new InventoryMovement(MovementId.generate(), itemId, toWarehouse,
                MovementType.RECEIPT, quantity, unitCost, currency, ref + "-IN", now);
        repository.postTransferIfAvailable(key, outbound, inbound, quantity);
    }

    /** Idempotent reservation creation using a stable caller command key. */
    public InventoryReservation reserve(String tenantId, InventoryCommandKey key, ItemId itemId, WarehouseId warehouseId,
                                        BigDecimal quantity, String uom, String sourceReference, Instant expiresAt) {
        Objects.requireNonNull(key, "Command key is required"); requirePositive(quantity, "Reserved quantity");
        Objects.requireNonNull(tenantId, "Tenant id is required");
        if (tenantId.isBlank()) throw new IllegalArgumentException("Tenant id must not be blank");
        return repository.reserveIfAvailable(key, InventoryReservation.create(tenantId, itemId, warehouseId, quantity, uom, sourceReference, expiresAt));
    }

    public InventoryReservation reserve(String tenantId, ItemId itemId, WarehouseId warehouseId,
                                        BigDecimal quantity, String uom, String sourceReference, Instant expiresAt) {
        requirePositive(quantity, "Reserved quantity");
        Objects.requireNonNull(tenantId, "Tenant id is required");
        if (tenantId.isBlank()) throw new IllegalArgumentException("Tenant id must not be blank");
        return repository.reserveIfAvailable(InventoryReservation.create(
                tenantId, itemId, warehouseId, quantity, uom, sourceReference, expiresAt));
    }

    /** Idempotently releases an active reservation; already released/expired reservations are returned unchanged. */
    public InventoryReservation releaseReservationIfActive(String tenantId, ReservationId id) {
        InventoryReservation current = reservationForTenant(tenantId, id);
        if (current.status() == ReservationStatus.RELEASED || current.status() == ReservationStatus.EXPIRED) return current;
        if (current.status() != ReservationStatus.ACTIVE) throw new IllegalStateException("Cannot release reservation in state " + current.status());
        return repository.updateReservation(current.release());
    }

    public InventoryReservation findReservation(String tenantId, ReservationId id) { return reservationForTenant(tenantId, id); }

    /**
     * Recovers a reservation after a lost command response. The key must be exactly
     * the same key used by reserve; an empty result is not proof of absence unless
     * the adapter provides durable, strongly consistent command receipts.
     */
    public Optional<InventoryReservation> findReservationByCommandKey(String tenantId, InventoryCommandKey key) {
        Objects.requireNonNull(key, "Command key is required");
        Objects.requireNonNull(tenantId, "Tenant id is required");
        if (tenantId.isBlank()) throw new IllegalArgumentException("Tenant id must not be blank");
        return repository.findReservationByCommandKey(key).map(reservation -> {
            if (!reservation.tenantId().equals(tenantId)) {
                throw new IllegalStateException("Reservation command resolved outside the requested tenant");
            }
            return reservation;
        });
    }

    public InventoryReservation releaseReservation(String tenantId, ReservationId id) {
        InventoryReservation current = reservationForTenant(tenantId, id);
        return repository.updateReservation(current.release());
    }

    public InventoryReservation expireReservation(String tenantId, ReservationId id) {
        InventoryReservation current = reservationForTenant(tenantId, id);
        if (current.expiresAt() == null || current.expiresAt().isAfter(Instant.now())) {
            throw new IllegalStateException("Reservation has not expired");
        }
        return repository.updateReservation(current.expire());
    }

    public InventoryMovement issueReserved(String tenantId, ReservationId reservationId, ItemId itemId,
                                           WarehouseId warehouseId, BigDecimal quantity, String currency, String ref) {
        requirePositive(quantity, "Issued quantity");
        InventoryReservation reservation = reservationForTenant(tenantId, reservationId);
        if (!reservation.itemId().equals(itemId) || !reservation.warehouseId().equals(warehouseId)) {
            throw new IllegalArgumentException("Issue does not match reservation");
        }
        StockBalance current = getStockBalance(itemId, warehouseId);
        BigDecimal unitCost = current.onHand().signum() > 0
                ? current.totalValue().divide(current.onHand(), 4, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        InventoryMovement movement = new InventoryMovement(MovementId.generate(), itemId, warehouseId,
                MovementType.ISSUE, quantity.negate(), unitCost, currency, ref, Instant.now());
        return repository.postReservedIssue(reservationId, movement, quantity);
    }

    /** Idempotent reserved issue; the same key and payload must replay the original movement. */
    public InventoryMovement issueReserved(String tenantId, InventoryCommandKey key, ReservationId reservationId,
                                           ItemId itemId, WarehouseId warehouseId, BigDecimal quantity,
                                           String currency, String ref) {
        Objects.requireNonNull(key, "Command key is required");
        requirePositive(quantity, "Issued quantity");
        InventoryReservation reservation = reservationForTenant(tenantId, reservationId);
        if (!reservation.itemId().equals(itemId) || !reservation.warehouseId().equals(warehouseId)) {
            throw new IllegalArgumentException("Issue does not match reservation");
        }
        StockBalance current = getStockBalance(itemId, warehouseId);
        BigDecimal unitCost = current.onHand().signum() > 0
                ? current.totalValue().divide(current.onHand(), 4, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        InventoryMovement movement = new InventoryMovement(MovementId.generate(), itemId, warehouseId,
                MovementType.ISSUE, quantity.negate(), unitCost, currency, ref, Instant.now());
        return repository.postReservedIssue(key, reservationId, movement, quantity);
    }

    private InventoryReservation reservationForTenant(String tenantId, ReservationId id) {
        Objects.requireNonNull(tenantId, "Tenant id is required");
        if (tenantId.isBlank()) throw new IllegalArgumentException("Tenant id must not be blank");
        InventoryReservation reservation = repository.findReservation(id);
        if (!reservation.tenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Reservation does not belong to the requested tenant");
        }
        return reservation;
    }

    /** Real-time stock balance projection derived from the movement log. */
    public StockBalance getStockBalance(ItemId itemId, WarehouseId warehouseId) {
        BigDecimal onHand = BigDecimal.ZERO;
        BigDecimal totalValue = BigDecimal.ZERO;

        for (InventoryMovement m : repository.findByItemAndWarehouse(itemId, warehouseId)) {
            onHand = onHand.add(m.quantity());
            if (m.type() == MovementType.RECEIPT || m.type() == MovementType.TRANSFER_IN) {
                totalValue = totalValue.add(m.totalCost());
            } else if (m.type() == MovementType.ISSUE || m.type() == MovementType.TRANSFER_OUT) {
                totalValue = totalValue.subtract(m.totalCost());
            }
        }
        if (totalValue.signum() < 0) totalValue = BigDecimal.ZERO;
        BigDecimal reserved = repository.findReservations(itemId, warehouseId).stream()
                .filter(r -> r.status() == ReservationStatus.ACTIVE)
                .filter(r -> r.expiresAt() == null || r.expiresAt().isAfter(Instant.now()))
                .map(InventoryReservation::quantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal available = onHand.subtract(reserved).max(BigDecimal.ZERO);
        return new StockBalance(itemId, warehouseId, onHand, reserved, available, totalValue);
    }

    public List<InventoryMovement> getMovements(ItemId itemId) {
        return repository.findByItem(itemId);
    }

    private static void requirePositive(BigDecimal quantity, String label) {
        Objects.requireNonNull(quantity, label + " is required");
        if (quantity.signum() <= 0) {
            throw new IllegalArgumentException(label + " must be positive");
        }
    }
}
