package tech.kayys.numou.workspace.application;

import tech.kayys.numou.workspace.application.WorkspacePorts.AuditEntry;
import tech.kayys.numou.workspace.domain.DatabaseSchemaMigrationJob;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence boundary for staged migrations. Implementations must commit staged record rows
 * and the progress checkpoint in one transaction, and perform final cutover atomically.
 */
public final class StagedSchemaMigrationPorts {
    private StagedSchemaMigrationPorts() {}

    public interface Repository {
        /**
         * Creates the job and audit row atomically. The adapter computes the authoritative active
         * record count rather than trusting the proposal's progress fields.
         */
        Optional<DatabaseSchemaMigrationJob> create(DatabaseSchemaMigrationJob proposal, AuditEntry audit);
        Optional<DatabaseSchemaMigrationJob> find(UUID tenantId, UUID jobId);

        /**
         * Stages at most the job's configured batch size, in deterministic record order.
         * Staging rows and checkpoint/progress update must commit together. Replaying the same
         * expected revision must not double-count staged records.
         */
        Optional<DatabaseSchemaMigrationJob> stageNextBatch(
                UUID tenantId, UUID jobId, long expectedJobRevision, UUID actorId, AuditEntry audit);

        /**
         * Applies only a READY job with a complete staging set. Must verify the table schema
         * version and every staged source-record revision, replace the schema and records,
         * append all record/schema/job audit entries, and mark the job APPLIED in one transaction.
         */
        boolean apply(UUID tenantId, UUID jobId, long expectedJobRevision,
                      AuditEntry schemaAudit, AuditEntry jobAudit);

        boolean cancel(UUID tenantId, UUID jobId, long expectedJobRevision, AuditEntry audit);
    }
}
