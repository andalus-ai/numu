package tech.kayys.numou.workspace.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A validated candidate value staged for a durable schema migration.
 * JSON is kept as an opaque persistence representation; record values never enter job logs.
 */
public record DatabaseRecordMigrationStage(
        UUID tenantId,
        UUID migrationId,
        UUID recordId,
        long expectedRecordRevision,
        String migratedRecordDataJson,
        Instant stagedAt) {
    public DatabaseRecordMigrationStage {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(migrationId, "migrationId");
        Objects.requireNonNull(recordId, "recordId");
        Objects.requireNonNull(migratedRecordDataJson, "migratedRecordDataJson");
        Objects.requireNonNull(stagedAt, "stagedAt");
        if (expectedRecordRevision < 1) {
            throw new IllegalArgumentException("expectedRecordRevision must be positive");
        }
        String json = migratedRecordDataJson.trim();
        if (json.isEmpty() || json.charAt(0) != '{' || json.charAt(json.length() - 1) != '}') {
            throw new IllegalArgumentException("migratedRecordDataJson must be a JSON object");
        }
    }
}
