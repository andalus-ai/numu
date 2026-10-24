package tech.kayys.numou.warehouse.application;

import java.time.Instant;
import java.util.Objects;

/** Immutable audit record for a human-initiated warehouse recovery operation. */
public record WarehouseRecoveryOperatorAuditEvent(
        String tenantId,
        String commandKey,
        String action,
        String actorId,
        String reason,
        long priorVersion,
        long resultingVersion,
        String correlationId,
        Instant occurredAt) {
    public WarehouseRecoveryOperatorAuditEvent {
        tenantId = required(tenantId, "tenantId");
        commandKey = required(commandKey, "commandKey");
        action = required(action, "action");
        actorId = required(actorId, "actorId");
        reason = required(reason, "reason");
        if (priorVersion < 1 || resultingVersion != priorVersion + 1) {
            throw new IllegalArgumentException("Audit versions must describe one successful transition");
        }
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (correlationId != null && correlationId.isBlank()) correlationId = null;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
