package tech.kayys.numou.workspace.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Durable control-plane state for a staged table-schema migration.
 * Record payloads are deliberately excluded; they live in the migration staging table.
 */
public record DatabaseSchemaMigrationJob(
        UUID id,
        UUID tenantId,
        UUID databaseId,
        UUID tableId,
        UUID requestedBy,
        long expectedSchemaVersion,
        long targetSchemaVersion,
        String targetTableName,
        String targetPropertiesJson,
        Status status,
        int batchSize,
        long totalRecords,
        long stagedRecords,
        UUID lastRecordId,
        long revision,
        String failureCode,
        String failureMessage,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt) {

    public DatabaseSchemaMigrationJob {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(databaseId, "databaseId");
        Objects.requireNonNull(tableId, "tableId");
        Objects.requireNonNull(requestedBy, "requestedBy");
        Objects.requireNonNull(targetTableName, "targetTableName");
        Objects.requireNonNull(targetPropertiesJson, "targetPropertiesJson");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (expectedSchemaVersion < 1 || targetSchemaVersion != expectedSchemaVersion + 1) {
            throw new IllegalArgumentException("target schema version must increment the expected version exactly once");
        }
        if (batchSize < 1 || batchSize > 500) throw new IllegalArgumentException("batchSize must be between 1 and 500");
        if (totalRecords < 0 || stagedRecords < 0 || stagedRecords > totalRecords) {
            throw new IllegalArgumentException("invalid migration progress");
        }
        if (revision < 1) throw new IllegalArgumentException("revision must be positive");
        if (status == Status.APPLIED && completedAt == null) {
            throw new IllegalArgumentException("applied migration must have completedAt");
        }
        if (status == Status.FAILED && (failureCode == null || failureCode.isBlank())) {
            throw new IllegalArgumentException("failed migration must have a failure code");
        }
    }

    public boolean terminal() {
        return status == Status.APPLIED || status == Status.FAILED || status == Status.CANCELLED;
    }

    public boolean readyToApply() {
        return status == Status.READY && stagedRecords == totalRecords;
    }

    public enum Status { PENDING, RUNNING, READY, APPLIED, FAILED, CANCELLED }
}
