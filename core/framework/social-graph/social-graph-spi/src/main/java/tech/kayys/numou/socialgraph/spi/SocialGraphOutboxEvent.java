package tech.kayys.numou.socialgraph.spi;

import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.socialgraph.domain.SocialConnectionId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Versioned event intent with actor provenance, appended atomically with graph state. */
public record SocialGraphOutboxEvent(UUID eventId, TenantId tenantId, PartyId actorPartyId,
        String aggregateType, UUID aggregateId, int aggregateVersion, String eventType,
        int schemaVersion, Instant occurredAt, String payloadJson) {
    public SocialGraphOutboxEvent {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(actorPartyId, "actorPartyId cannot be null");
        if (aggregateType == null || aggregateType.isBlank() || aggregateType.length() > 48)
            throw new IllegalArgumentException("aggregateType is required and must be at most 48 characters");
        Objects.requireNonNull(aggregateId, "aggregateId cannot be null");
        if (aggregateVersion < 0 || schemaVersion < 1) throw new IllegalArgumentException("Invalid event version");
        if (eventType == null || eventType.isBlank()) throw new IllegalArgumentException("eventType is required");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        Objects.requireNonNull(payloadJson, "payloadJson cannot be null");
    }

    /** Source-compatible constructor for the existing bilateral connection event contract. */
    public SocialGraphOutboxEvent(UUID eventId, TenantId tenantId, PartyId actorPartyId,
            SocialConnectionId aggregateId, int aggregateVersion, String eventType,
            int schemaVersion, Instant occurredAt, String payloadJson) {
        this(eventId, tenantId, actorPartyId, "CONNECTION", aggregateId.value(),
                aggregateVersion, eventType, schemaVersion, occurredAt, payloadJson);
    }
}
