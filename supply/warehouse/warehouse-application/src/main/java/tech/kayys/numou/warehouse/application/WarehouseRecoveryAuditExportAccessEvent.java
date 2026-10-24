package tech.kayys.numou.warehouse.application;

import java.time.Instant;
import java.util.Objects;

/** Append-only record of access to an export or its verification outcome. */
public record WarehouseRecoveryAuditExportAccessEvent(
        String eventId, String tenantId, String exportId, String actorId,
        Action action, Outcome outcome, String reasonCode, Instant occurredAt) {
    public enum Action { REQUEST, CLAIM, RESUME, DOWNLOAD, VERIFY, VERIFY_FAILED }
    public enum Outcome { ALLOWED, DENIED, SUCCESS, FAILURE }

    public WarehouseRecoveryAuditExportAccessEvent {
        eventId = required(eventId, "eventId");
        tenantId = required(tenantId, "tenantId");
        exportId = required(exportId, "exportId");
        actorId = required(actorId, "actorId");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (reasonCode != null && reasonCode.length() > 120)
            throw new IllegalArgumentException("reasonCode must be at most 120 characters");
    }
    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
}
