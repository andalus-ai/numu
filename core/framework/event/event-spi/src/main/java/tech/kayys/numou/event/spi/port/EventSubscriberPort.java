package tech.kayys.numou.event.spi.port;

import tech.kayys.numou.event.domain.BusinessEvent;
import tech.kayys.numou.event.domain.EventEnvelope;

import java.util.function.Function;

/**
 * Inbound event consumption.
 *
 * <p>The handler is expected to be idempotent: redelivery of the same
 * event id must be harmless (base01.md §P1-15). Implementations are
 * responsible for redelivery semantics, not for deduplication.
 */
public interface EventSubscriberPort {

    /**
     * Subscribes to one event type.
     *
     * @param eventType the dotted event type, e.g. logistics.shipment.delivered
     * @param handler   the consumer
     * @return a handle used to unsubscribe
     */
    SubscriptionHandle subscribe(
            String eventType,
            Function<EventEnvelope<? extends BusinessEvent>, io.smallrye.mutiny.Uni<Void>> handler);

    /** Cancels a subscription. */
    void unsubscribe(SubscriptionHandle handle);

    /** Opaque subscription identity. */
    interface SubscriptionHandle {
        String id();
    }
}
