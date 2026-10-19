package tech.kayys.numou.inventory.domain.reservation;

import tech.kayys.numou.inventory.domain.inventory.ItemId;
import tech.kayys.numou.inventory.domain.inventory.WarehouseId;
import tech.kayys.numou.inventory.domain.location.StorageLocationId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * First-class inventory reservation representing stock commitment.
 */
public record InventoryReservation(
        ReservationId id,
        String tenantId,
        ItemId itemId,
        WarehouseId warehouseId,
        StorageLocationId storageLocationId,
        BigDecimal quantity,
        String uom,
        ReservationStatus status,
        String sourceReference,
        Instant createdAt,
        Instant expiresAt,
        int version
) {
    public InventoryReservation {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(itemId, "itemId cannot be null");
        Objects.requireNonNull(warehouseId, "warehouseId cannot be null");
        Objects.requireNonNull(quantity, "quantity cannot be null");
        Objects.requireNonNull(uom, "uom cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
        Objects.requireNonNull(sourceReference, "sourceReference cannot be null");
        Objects.requireNonNull(createdAt, "createdAt cannot be null");

        if (quantity.signum() <= 0) {
            throw new IllegalArgumentException("Reservation quantity must be positive: " + quantity);
        }

        if (version < 1) {
            throw new IllegalArgumentException("Version must be at least 1");
        }
    }

    public static InventoryReservation create(
            String tenantId,
            ItemId itemId,
            WarehouseId warehouseId,
            BigDecimal quantity,
            String uom,
            String sourceReference,
            Instant expiresAt
    ) {
        return new InventoryReservation(
                ReservationId.generate(),
                tenantId,
                itemId,
                warehouseId,
                null,
                quantity,
                uom,
                ReservationStatus.ACTIVE,
                sourceReference,
                Instant.now(),
                expiresAt,
                1
        );
    }

    public InventoryReservation commit() {
        if (status != ReservationStatus.ACTIVE) {
            throw new IllegalStateException("Only ACTIVE reservations can be committed. Current: " + status);
        }
        return new InventoryReservation(
                id, tenantId, itemId, warehouseId, storageLocationId, quantity, uom,
                ReservationStatus.COMMITTED, sourceReference, createdAt, expiresAt, version + 1
        );
    }

    public InventoryReservation release() {
        if (status != ReservationStatus.ACTIVE) {
            throw new IllegalStateException("Only ACTIVE reservations can be released. Current: " + status);
        }
        return new InventoryReservation(
                id, tenantId, itemId, warehouseId, storageLocationId, quantity, uom,
                ReservationStatus.RELEASED, sourceReference, createdAt, expiresAt, version + 1
        );
    }

    public InventoryReservation expire() {
        if (status != ReservationStatus.ACTIVE) {
            throw new IllegalStateException("Only ACTIVE reservations can be expired. Current: " + status);
        }
        return new InventoryReservation(
                id, tenantId, itemId, warehouseId, storageLocationId, quantity, uom,
                ReservationStatus.EXPIRED, sourceReference, createdAt, expiresAt, version + 1
        );
    }
}
