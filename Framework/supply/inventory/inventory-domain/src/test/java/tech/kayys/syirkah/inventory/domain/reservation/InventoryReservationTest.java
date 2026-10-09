package tech.kayys.syirkah.inventory.domain.reservation;

import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.inventory.domain.inventory.ItemId;
import tech.kayys.syirkah.inventory.domain.inventory.WarehouseId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

class InventoryReservationTest {

    @Test
    void testReservationLifecycle() {
        ItemId itemId = ItemId.generate();
        WarehouseId whId = WarehouseId.generate();
        Instant expiresAt = Instant.now().plus(2, ChronoUnit.HOURS);

        InventoryReservation reservation = InventoryReservation.create(
                "TENANT-1", itemId, whId, new BigDecimal("5"), "PCS", "ORDER-100", expiresAt
        );

        assertEquals(ReservationStatus.ACTIVE, reservation.status());
        assertEquals(1, reservation.version());

        // Commit reservation
        InventoryReservation committed = reservation.commit();
        assertEquals(ReservationStatus.COMMITTED, committed.status());
        assertEquals(2, committed.version());

        // Cannot commit again or release committed
        assertThrows(IllegalStateException.class, committed::commit);
        assertThrows(IllegalStateException.class, committed::release);
    }

    @Test
    void testReservationReleaseAndExpire() {
        ItemId itemId = ItemId.generate();
        WarehouseId whId = WarehouseId.generate();

        InventoryReservation reservation = InventoryReservation.create(
                "TENANT-1", itemId, whId, new BigDecimal("10"), "PCS", "ORDER-101", null
        );

        InventoryReservation released = reservation.release();
        assertEquals(ReservationStatus.RELEASED, released.status());

        InventoryReservation reservation2 = InventoryReservation.create(
                "TENANT-1", itemId, whId, new BigDecimal("10"), "PCS", "ORDER-102", null
        );

        InventoryReservation expired = reservation2.expire();
        assertEquals(ReservationStatus.EXPIRED, expired.status());
    }

    @Test
    void testRejectsZeroOrNegativeQuantity() {
        ItemId itemId = ItemId.generate();
        WarehouseId whId = WarehouseId.generate();

        assertThrows(IllegalArgumentException.class, () ->
                InventoryReservation.create("TENANT-1", itemId, whId, BigDecimal.ZERO, "PCS", "ORDER-1", null)
        );

        assertThrows(IllegalArgumentException.class, () ->
                InventoryReservation.create("TENANT-1", itemId, whId, new BigDecimal("-5"), "PCS", "ORDER-1", null)
        );
    }
}
