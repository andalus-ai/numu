package tech.kayys.numou.asset.application.integration;

import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.util.UUID;

/**
 * Builds versioned {@link EventEnvelope}s for Asset domain events (ASSET-28 §7).
 *
 * <p>The schema version is explicit and part of the contract so consumers can
 * detect incompatible changes; additive fields keep the version stable
 * (§44, §45).</p>
 */
public final class AssetEventEnvelopeFactory {

    public static final String AGGREGATE_TYPE = "Asset";
    public static final String SCHEMA_VERSION = "v1";

    private AssetEventEnvelopeFactory() {
    }

    public static EventEnvelope of(
            String tenantId, UUID aggregateId, DomainEvent event, String correlationId, String causationId) {
        return new EventEnvelope(
                event.eventId(),
                tenantId,
                event.eventType(),
                SCHEMA_VERSION,
                AGGREGATE_TYPE,
                aggregateId,
                event.occurredAt(),
                correlationId,
                causationId,
                event);
    }
}