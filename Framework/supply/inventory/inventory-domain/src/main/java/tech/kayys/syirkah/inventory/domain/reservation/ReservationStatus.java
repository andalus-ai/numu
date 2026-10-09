package tech.kayys.syirkah.inventory.domain.reservation;

/**
 * Lifecycle status of an inventory reservation.
 */
public enum ReservationStatus {
    ACTIVE,
    COMMITTED,
    RELEASED,
    EXPIRED
}
