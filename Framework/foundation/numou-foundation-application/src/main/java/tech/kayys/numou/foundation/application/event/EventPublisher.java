package tech.kayys.numou.foundation.application.event;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.util.List;

/**
 * Outbound port for publishing domain events raised by an aggregate.
 *
 * The application layer only knows this interface - never Kafka
 * directly. A concrete adapter (e.g. a transactional-outbox-backed
 * Kafka publisher) is provided by each service's infrastructure layer.
 */
public interface EventPublisher {

    Uni<Void> publish(List<DomainEvent> events);

}
