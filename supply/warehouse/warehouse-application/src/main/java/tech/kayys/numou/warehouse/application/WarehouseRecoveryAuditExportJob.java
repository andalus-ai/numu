package tech.kayys.numou.warehouse.application;

import java.time.Instant;
import java.util.Objects;

/** Durable state for a recovery-audit export; checkpoints follow durable output writes. */
public record WarehouseRecoveryAuditExportJob(
        String exportId, String tenantId, String requestedBy, Status status,
        String continuationCursor, long exportedEventCount, long exportedByteCount,
        String artifactLocation, String failureCode, Instant createdAt, Instant updatedAt,
        Instant leaseUntil, String leaseOwner, long version) {
    public enum Status { REQUESTED, RUNNING, VERIFYING, COMPLETED, FAILED }

    public WarehouseRecoveryAuditExportJob {
        exportId = required(exportId, "exportId");
        tenantId = required(tenantId, "tenantId");
        requestedBy = required(requestedBy, "requestedBy");
        Objects.requireNonNull(status, "status");
        if (exportedEventCount < 0 || exportedByteCount < 0 || version < 0)
            throw new IllegalArgumentException("counts and version must not be negative");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) throw new IllegalArgumentException("updatedAt precedes createdAt");
        if (status == Status.COMPLETED && (artifactLocation == null || artifactLocation.isBlank()))
            throw new IllegalArgumentException("completed export requires an artifact location");
        if (status == Status.FAILED && (failureCode == null || failureCode.isBlank()))
            throw new IllegalArgumentException("failed export requires a sanitized failure code");
        if (status == Status.RUNNING && (leaseOwner == null || leaseOwner.isBlank() || leaseUntil == null))
            throw new IllegalArgumentException("running export requires a worker lease");
    }

    public WarehouseRecoveryAuditExportJob claim(String worker, Instant now, Instant until) {
        if (status != Status.REQUESTED && status != Status.RUNNING)
            throw new IllegalStateException("only requested or resumable running jobs may be claimed");
        required(worker, "worker");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(until, "until");
        if (!until.isAfter(now)) throw new IllegalArgumentException("lease must end after now");
        if (status == Status.RUNNING && leaseUntil.isAfter(now) && !worker.equals(leaseOwner))
            throw new IllegalStateException("job is leased by another worker");
        return new WarehouseRecoveryAuditExportJob(exportId, tenantId, requestedBy, Status.RUNNING,
                continuationCursor, exportedEventCount, exportedByteCount, artifactLocation, null,
                createdAt, now, until, worker, version + 1);
    }

    public WarehouseRecoveryAuditExportJob checkpoint(String worker, String nextCursor,
            long events, long bytes, Instant now, Instant renewedLeaseUntil) {
        requireLease(worker, now);
        if (events < exportedEventCount || bytes < exportedByteCount)
            throw new IllegalArgumentException("checkpoint counters must be monotonic");
        if (events == exportedEventCount && bytes == exportedByteCount
                && !Objects.equals(continuationCursor, nextCursor))
            throw new IllegalArgumentException("cursor cannot advance without durable output progress");
        if (renewedLeaseUntil == null || !renewedLeaseUntil.isAfter(now))
            throw new IllegalArgumentException("renewed lease must end after now");
        return new WarehouseRecoveryAuditExportJob(exportId, tenantId, requestedBy, Status.RUNNING,
                nextCursor, events, bytes, artifactLocation, null, createdAt, now,
                renewedLeaseUntil, worker, version + 1);
    }

    /** Releases a checkpointed non-terminal page so another invocation can resume immediately. */
    public WarehouseRecoveryAuditExportJob yieldForResume(String worker, Instant now) {
        requireLease(worker, now);
        if (continuationCursor == null) throw new IllegalStateException("completed pagination cannot be yielded for resume");
        return new WarehouseRecoveryAuditExportJob(exportId, tenantId, requestedBy, Status.REQUESTED,
                continuationCursor, exportedEventCount, exportedByteCount, artifactLocation, null,
                createdAt, now, null, null, version + 1);
    }

    public WarehouseRecoveryAuditExportJob beginVerification(String worker, Instant now, String location) {
        requireLease(worker, now);
        if (location == null || location.isBlank()) throw new IllegalArgumentException("artifact location is required");
        return new WarehouseRecoveryAuditExportJob(exportId, tenantId, requestedBy, Status.VERIFYING,
                continuationCursor, exportedEventCount, exportedByteCount, location.trim(), null,
                createdAt, now, null, null, version + 1);
    }

    public WarehouseRecoveryAuditExportJob complete(Instant now) {
        if (status != Status.VERIFYING) throw new IllegalStateException("only a verifying job may complete");
        return new WarehouseRecoveryAuditExportJob(exportId, tenantId, requestedBy, Status.COMPLETED,
                continuationCursor, exportedEventCount, exportedByteCount, artifactLocation, null,
                createdAt, now, null, null, version + 1);
    }

    public WarehouseRecoveryAuditExportJob fail(Instant now, String sanitizedFailureCode) {
        if (status == Status.COMPLETED) throw new IllegalStateException("completed export cannot fail");
        return new WarehouseRecoveryAuditExportJob(exportId, tenantId, requestedBy, Status.FAILED,
                continuationCursor, exportedEventCount, exportedByteCount, artifactLocation,
                required(sanitizedFailureCode, "sanitizedFailureCode"), createdAt, now, null, null, version + 1);
    }

    private void requireLease(String worker, Instant now) {
        if (status != Status.RUNNING || !Objects.equals(leaseOwner, worker)
                || leaseUntil == null || !leaseUntil.isAfter(now))
            throw new IllegalStateException("worker does not hold a live export lease");
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
}
