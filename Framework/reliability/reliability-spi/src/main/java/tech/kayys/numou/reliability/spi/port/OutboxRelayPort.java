package tech.kayys.numou.reliability.spi.port;

import io.smallrye.mutiny.Uni;

/**
 * Drains the outbox to the message transport.
 *
 * <p>Kept separate from the store so "where we queue" (Postgres table) and
 * "where we publish" (Kafka, RabbitMQ, webhook fan-out) can evolve
 * independently.
 */
public interface OutboxRelayPort {

    /** Publishes one entry. Returning successfully marks it published. */
    Uni<Void> relay(tech.kayys.numou.reliability.outbox.OutboxEntry entry);

    /** Runs one drain cycle and returns how many entries were relayed. */
    Uni<Integer> runOnce();
}
