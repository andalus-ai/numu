package tech.kayys.syirkah.accounting.application.outbox;

import tech.kayys.syirkah.accounting.domain.ledger.LedgerId;
import tech.kayys.syirkah.accounting.domain.multitenancy.TenantRef;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Outbox record persisted atomically with financial aggregates.
 */
public record OutboxEvent(
        UUID id,
        String aggregateType,
        String aggregateId,
        String eventType,
        TenantRef tenantId,
        LedgerId ledgerId,
        String payload,
        Instant createdAt,
        Instant processedAt
) {
    public OutboxEvent {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(aggregateType, "aggregateType cannot be null");
        Objects.requireNonNull(aggregateId, "aggregateId cannot be null");
        Objects.requireNonNull(eventType, "eventType cannot be null");
        Objects.requireNonNull(createdAt, "createdAt cannot be null");
    }

    public static OutboxEvent of(String aggregateType, String aggregateId, String eventType, TenantRef tenantId, LedgerId ledgerId, String payload) {
        return new OutboxEvent(UUID.randomUUID(), aggregateType, aggregateId, eventType, tenantId, ledgerId, payload, Instant.now(), null);
    }

    public OutboxEvent markProcessed() {
        return new OutboxEvent(id, aggregateType, aggregateId, eventType, tenantId, ledgerId, payload, createdAt, Instant.now());
    }

    public boolean isProcessed() {
        return processedAt != null;
    }
}
