package tech.kayys.numou.reservation.adapter.postgres;

import io.smallrye.mutiny.Uni;

/**
 * Transport-neutral destination for a durable reservation outbox event.
 *
 * <p>Implementations adapt this contract to a broker, webhook dispatcher, or in-process
 * integration. The event ID is the delivery idempotency key: delivery is at-least-once,
 * so consumers must deduplicate it.</p>
 */
@FunctionalInterface
public interface ReservationOutboxPublisher {
    Uni<Void> publish(ReservationOutboxMessage message);
}
