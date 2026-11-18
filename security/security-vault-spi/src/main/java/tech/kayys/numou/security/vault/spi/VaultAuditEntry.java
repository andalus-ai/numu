package tech.kayys.numou.security.vault.spi;

import java.time.Instant;
import java.util.Objects;

/** Minimal immutable audit facts persisted with a vault mutation. Never contains secret material. */
public record VaultAuditEntry(String actor, String eventType, String details, Instant occurredAt) {
    public VaultAuditEntry {
        actor = required(actor, "actor", 200);
        eventType = required(eventType, "eventType", 100);
        details = details == null ? "" : details;
        if (details.length() > 1000) throw new IllegalArgumentException("details is too long");
        Objects.requireNonNull(occurredAt, "occurredAt is required");
    }

    private static String required(String value, String field, int max) {
        if (value == null || value.isBlank() || value.length() > max)
            throw new IllegalArgumentException(field + " must contain 1.." + max + " characters");
        return value;
    }
}
