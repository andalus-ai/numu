package tech.kayys.syirkah.accounting.domain.hardening;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Tamper-evident audit log entry. */
public record AuditRecord(
        String id,
        String principal,
        AuditAction action,
        String targetAggregateType,
        String targetAggregateId,
        String detail,
        Instant occurredAt
) {
    public AuditRecord {
        Objects.requireNonNull(id);
        Objects.requireNonNull(principal);
        Objects.requireNonNull(action);
        Objects.requireNonNull(targetAggregateType);
        Objects.requireNonNull(targetAggregateId);
        Objects.requireNonNull(occurredAt);
    }
    public static AuditRecord of(String user, AuditAction act, String type, String aggId, String msg) {
        return new AuditRecord(UUID.randomUUID().toString(), user, act, type, aggId, msg, Instant.now());
    }
}
