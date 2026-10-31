package tech.kayys.numou.queuemanagement.spi;

import java.time.Instant;
import java.util.UUID;

/** Read-only operator view of a terminal outbox delivery. */
public record QueueDeadLetterMessage(
        UUID eventId, UUID tenantId, String eventType, int schemaVersion,
        String aggregateType, String aggregateId, String correlationId,
        Instant occurredAt, String payload, int attemptCount,
        Instant deadLetteredAt, String lastError, int replayCount) { }
