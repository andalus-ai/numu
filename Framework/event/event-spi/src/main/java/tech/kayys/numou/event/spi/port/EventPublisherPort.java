package tech.kayys.numou.event.spi.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.event.domain.BusinessEvent;
import tech.kayys.numou.event.domain.EventEnvelope;

import java.util.List;

/**
 * Outbound event publication. Implemented by Kafka, RabbitMQ, an HTTP
 * webhook dispatcher, or an in-memory test double - the application layer
 * never learns which (base01.md §P1-13, §P1-16).
 */
public interface EventPublisherPort {

    Uni<Void> publish(EventEnvelope<? extends BusinessEvent> envelope);

    /**
     * Publishes a batch in order. Implementations must preserve the order
     * so a consumer observing prefix N sees a causally consistent slice.
     */
    Uni<Void> publishAll(List<EventEnvelope<? extends BusinessEvent>> envelopes);
}
