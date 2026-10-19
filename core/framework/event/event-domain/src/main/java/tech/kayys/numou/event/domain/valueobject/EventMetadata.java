package tech.kayys.numou.event.domain.valueobject;

import tech.kayys.numou.foundation.domain.valueobject.ValueObject;
import tech.kayys.numou.event.domain.identifier.CausationId;
import tech.kayys.numou.event.domain.identifier.CorrelationId;
import tech.kayys.numou.event.domain.identifier.EventId;

import java.util.UUID;

/**
 * The context an event travelled with (base01.md §P1-11, §P1-19).
 *
 * <p>Kept separate from the payload so business consumers can read only
 * the payload, while observability, security and audit read only the
 * metadata - and neither has to know the other's shape.
 *
 * @param eventId       identity of this occurrence (deduplication key)
 * @param correlationId the whole business journey this belongs to
 * @param causationId   the step that directly produced it
 * @param tenantId      tenant isolation boundary (may be null for platform events)
 * @param participantId ecosystem participant that emitted it
 * @param actorId       user or system principal that acted
 * @param occurredAt    when it happened, as a monotonic-ish ISO instant
 * @param traceId       distributed tracing correlation
 */
public record EventMetadata(
        EventId eventId,
        CorrelationId correlationId,
        CausationId causationId,
        UUID tenantId,
        UUID participantId,
        String actorId,
        String occurredAt,
        String traceId) implements ValueObject {

    /**
     * Builds metadata for an event raised inside a tenant-scoped operation.
     */
    public static EventMetadata of(
            UUID tenantId,
            UUID participantId,
            String actorId,
            CorrelationId correlationId) {
        return new EventMetadata(
                EventId.generate(),
                correlationId,
                null,
                tenantId,
                participantId,
                actorId,
                java.time.Instant.now().toString(),
                null);
    }

    /** Returns a copy caused by the given event - used when chaining. */
    public EventMetadata causedBy(EventId cause) {
        return new EventMetadata(
                eventId,
                correlationId,
                CausationId.ofEvent(cause),
                tenantId,
                participantId,
                actorId,
                occurredAt,
                traceId);
    }
}
