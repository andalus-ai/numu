package tech.kayys.syirkah.document.adapter.outbound.postgres;

import java.time.Instant;
import java.util.UUID;

public record DocumentOutboxMessage(
        UUID id,
        UUID eventId,
        UUID tenantId,
        String aggregateType,
        String aggregateId,
        String eventType,
        String payload,
        Instant occurredAt,
        int attemptCount
) {}
