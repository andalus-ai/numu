package tech.kayys.numou.asset.application.integration;

import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Versioned integration envelope wrapping a domain event (ASSET-28 §7, §20).
 *
 * <p>The pure {@link DomainEvent} stays minimal; tenant/correlation/causation
 * metadata and the schema version are added only here, at the messaging
 * boundary, so the domain never carries transport concerns.</p>
 */
public record EventEnvelope(
        UUID eventId,
        String tenantId,
        String eventType,
        String schemaVersion,
        String aggregateType,
        UUID aggregateId,
        Instant occurredAt,
        String correlationId,
        String causationId,
        DomainEvent payload
) {

    public EventEnvelope {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(eventType, "eventType cannot be null");
        Objects.requireNonNull(schemaVersion, "schemaVersion cannot be null");
        Objects.requireNonNull(aggregateType, "aggregateType cannot be null");
        Objects.requireNonNull(aggregateId, "aggregateId cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        Objects.requireNonNull(payload, "payload cannot be null");
    }
}