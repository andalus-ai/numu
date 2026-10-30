package tech.kayys.numou.event.adapter.kafka;

import tech.kayys.numou.event.domain.BusinessEvent;
import tech.kayys.numou.event.domain.valueobject.EventVersion;

import java.util.Optional;

/**
 * Application-owned allow-list for event payload contracts.
 *
 * <p>Implementations must register only concrete, reviewed event classes.
 * The serializer never resolves classes from data received over the wire.
 */
public interface EventTypeRegistry {

    Optional<Class<? extends BusinessEvent>> find(String eventType, EventVersion version);
}
