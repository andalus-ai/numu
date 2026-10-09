package tech.kayys.syirkah.restaurant.reservation.domain;

public enum ReservationStatus {
    /** Held, but the required deposit has not been paid; tables are held for a limited time. */
    PENDING_DEPOSIT,
    CONFIRMED,
    SEATED,
    COMPLETED,
    CANCELLED,
    NO_SHOW;

    public boolean isActive() {
        return this == PENDING_DEPOSIT || this == CONFIRMED || this == SEATED;
    }
}
