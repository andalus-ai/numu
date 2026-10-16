package tech.kayys.syirkah.reservation.domain;

/**
 * Lifecycle states of a reservation.
 */
public enum ReservationStatus {
    REQUESTED,
    PENDING_DEPOSIT,
    CONFIRMED,
    CHECKED_IN,
    SEATED,       // Dining alias for CHECKED_IN
    COMPLETED,
    CANCELLED,
    NO_SHOW;

    public boolean isActive() {
        return this == REQUESTED || this == PENDING_DEPOSIT || this == CONFIRMED || this == CHECKED_IN || this == SEATED;
    }

    public boolean isAdmitted() {
        return this == CHECKED_IN || this == SEATED;
    }
}
