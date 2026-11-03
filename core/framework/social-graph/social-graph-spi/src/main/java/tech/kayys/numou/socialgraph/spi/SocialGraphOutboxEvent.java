package tech.kayys.numou.socialgraph.spi;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.socialgraph.domain.SocialConnectionId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Versioned, minimal event intent appended in the same transaction as graph state. */
public record SocialGraphOutboxEvent(UUID eventId, TenantId tenantId,
        SocialConnectionId aggregateId, int aggregateVersion, String eventType,
        int schemaVersion, Instant occurredAt, String payloadJson) {
    public SocialGraphOutboxEvent {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(aggregateId, "aggregateId cannot be null");
        if (aggregateVersion < 0 || schemaVersion < 1) throw new IllegalArgumentException("Invalid event version");
        if (eventType == null || eventType.isBlank()) throw new IllegalArgumentException("eventType is required");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        Objects.requireNonNull(payloadJson, "payloadJson cannot be null");
    }
}
