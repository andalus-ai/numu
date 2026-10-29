package tech.kayys.numou.queuemanagement.spi;

import java.util.List;
import java.util.Objects;

/** Mutation result plus the audit and outbox records committed with it. */
public record QueueCommandResult<T>(
        T value,
        List<QueueAuditRecord> auditRecords,
        List<QueueEventRecord> events) {
    public QueueCommandResult {
        Objects.requireNonNull(value, "value must not be null");
        auditRecords = List.copyOf(Objects.requireNonNull(auditRecords, "auditRecords must not be null"));
        events = List.copyOf(Objects.requireNonNull(events, "events must not be null"));
    }
}