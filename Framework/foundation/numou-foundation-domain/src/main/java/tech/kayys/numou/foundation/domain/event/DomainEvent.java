package tech.kayys.numou.foundation.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents something meaningful that happened in the domain.
 *
 * Deliberately minimal - no tenantId/aggregateId/correlationId here.
 * Those are integration/message metadata that belongs to the
 * messaging/platform layer, not the pure domain event abstraction.
 */
public interface DomainEvent {

    UUID eventId();

    Instant occurredAt();

    String eventType();

}
