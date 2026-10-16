package tech.kayys.syirkah.restaurant.kitchen.domain;

public enum TicketStatus {
    NEW, IN_PROGRESS, READY,
    /** Handed to the expo/server; off the KDS. */
    BUMPED,
    CANCELLED;

    public boolean isOnScreen() {
        return this == NEW || this == IN_PROGRESS || this == READY;
    }

    public boolean isCooking() {
        return this == NEW || this == IN_PROGRESS;
    }
}
