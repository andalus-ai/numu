package tech.kayys.syirkah.construction.spi.outbox;

import io.smallrye.mutiny.Uni;

/** Broker or event-platform adapter. Delivery is at-least-once, not exactly-once. */
public interface OutboxDeliveryPort {
    Uni<Void> deliver(OutboxRelayStore.ClaimedOutboxEvent event);
}
