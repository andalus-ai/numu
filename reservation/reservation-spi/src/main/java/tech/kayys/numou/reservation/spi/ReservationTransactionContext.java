package tech.kayys.numou.reservation.spi;

import java.util.Objects;

/**
 * Capabilities bound to one reservation command transaction.
 * All participating repository operations and event persistence must use that transaction.
 */
public record ReservationTransactionContext(
        IdempotentReservationRepository reservations,
        LedgerRepository ledgers,
        DomainEventSink events) {
    public ReservationTransactionContext {
        Objects.requireNonNull(reservations, "reservations");
        Objects.requireNonNull(ledgers, "ledgers");
        Objects.requireNonNull(events, "events");
    }
}
