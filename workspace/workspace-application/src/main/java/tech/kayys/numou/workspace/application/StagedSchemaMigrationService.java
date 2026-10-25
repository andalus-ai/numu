package tech.kayys.numou.workspace.application;

import tech.kayys.numou.workspace.application.WorkspacePorts.AccessPolicy;
import tech.kayys.numou.workspace.application.WorkspacePorts.Action;
import tech.kayys.numou.workspace.application.WorkspacePorts.AuditEntry;
import tech.kayys.numou.workspace.application.WorkspacePorts.Resource;
import tech.kayys.numou.workspace.application.WorkspacePorts.StructuredDatabaseRepository;
import tech.kayys.numou.workspace.application.StagedSchemaMigrationPorts.Repository;
import tech.kayys.numou.workspace.domain.DatabaseSchemaMigrationJob;
import tech.kayys.numou.workspace.domain.DatabaseTable;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Application use cases for staged schema migration. Batch processing and cutover are delegated
 * to a repository transaction boundary so a checkpoint can never get ahead of durable staged data.
 */
public final class StagedSchemaMigrationService {
    private final StructuredDatabaseRepository databases;
    private final Repository migrations;
    private final AccessPolicy accessPolicy;
    private final Clock clock;

    public StagedSchemaMigrationService(StructuredDatabaseRepository databases, Repository migrations,
                                        AccessPolicy accessPolicy, Clock clock) {
        this.databases = Objects.requireNonNull(databases, "databases");
        this.migrations = Objects.requireNonNull(migrations, "migrations");
        this.accessPolicy = Objects.requireNonNull(accessPolicy, "accessPolicy");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Registers a migration proposal. The adapter computes the authoritative active-record count
     * and persists the job plus audit event atomically; caller-supplied progress is not trusted.
     */
    public DatabaseSchemaMigrationJob create(UUID tenantId, UUID actorId, DatabaseSchemaMigrationJob proposal) {
        Objects.requireNonNull(proposal, "proposal");
        if (!tenantId.equals(proposal.tenantId()) || !actorId.equals(proposal.requestedBy())) {
            throw new SecurityException("migration tenant and requester must match the authenticated context");
        }
        accessPolicy.require(tenantId, actorId, Resource.DATABASE, proposal.databaseId(), Action.UPDATE);
        accessPolicy.require(tenantId, actorId, Resource.DATABASE_TABLE, proposal.tableId(), Action.UPDATE);
        DatabaseTable table = databases.findTable(tenantId, proposal.databaseId(), proposal.tableId())
                .orElseThrow(() -> new IllegalArgumentException("table not found in tenant database"));
        if (table.schemaVersion() != proposal.expectedSchemaVersion()) {
            throw new IllegalArgumentException("stale table schema version; refresh before creating migration");
        }
        if (proposal.status() != DatabaseSchemaMigrationJob.Status.PENDING || proposal.stagedRecords() != 0
                || proposal.lastRecordId() != null || proposal.revision() != 1) {
            throw new IllegalArgumentException("new migration must be pending at revision 1 with no checkpoint");
        }
        Instant now = clock.instant();
        AuditEntry audit = audit(tenantId, actorId, "DATABASE_SCHEMA_MIGRATION_CREATED",
                proposal.id(), 1, "Created staged schema migration");
        return migrations.create(proposal, audit)
                .orElseThrow(() -> new IllegalStateException("migration creation conflicted with another job or schema update"));
    }

    public DatabaseSchemaMigrationJob stageNextBatch(UUID tenantId, UUID actorId, UUID jobId,
                                                       long expectedJobRevision) {
        DatabaseSchemaMigrationJob job = requireJob(tenantId, actorId, jobId, Action.UPDATE);
        requireExpectedRevision(job, expectedJobRevision);
        if (job.terminal() || job.status() == DatabaseSchemaMigrationJob.Status.READY) {
            throw new IllegalStateException("migration is not eligible for another batch");
        }
        AuditEntry audit = audit(tenantId, actorId, "DATABASE_SCHEMA_MIGRATION_BATCH_STAGED",
                job.id(), job.revision() + 1, "Staged next migration batch");
        return migrations.stageNextBatch(tenantId, jobId, expectedJobRevision, actorId, audit)
                .orElseThrow(() -> new IllegalStateException("migration checkpoint conflict; reload job before retrying"));
    }

    public DatabaseSchemaMigrationJob apply(UUID tenantId, UUID actorId, UUID jobId,
                                              long expectedJobRevision) {
        DatabaseSchemaMigrationJob job = requireJob(tenantId, actorId, jobId, Action.UPDATE);
        requireExpectedRevision(job, expectedJobRevision);
        if (!job.readyToApply()) {
            throw new IllegalStateException("migration must be READY with all records staged before cutover");
        }
        Instant now = clock.instant();
        AuditEntry schemaAudit = new AuditEntry(tenantId, actorId, "DATABASE_TABLE_SCHEMA_MIGRATED",
                "DATABASE_TABLE", job.tableId(), job.targetSchemaVersion(),
                "Applied staged schema migration " + job.id(), now);
        AuditEntry jobAudit = audit(tenantId, actorId, "DATABASE_SCHEMA_MIGRATION_APPLIED",
                job.id(), job.revision() + 1, "Applied staged schema migration");
        if (!migrations.apply(tenantId, jobId, expectedJobRevision, schemaAudit, jobAudit)) {
            throw new IllegalStateException("schema cutover conflicted with a schema or record update; no changes were applied");
        }
        return migrations.find(tenantId, jobId)
                .orElseThrow(() -> new IllegalStateException("applied migration job disappeared"));
    }

    public DatabaseSchemaMigrationJob cancel(UUID tenantId, UUID actorId, UUID jobId,
                                               long expectedJobRevision) {
        DatabaseSchemaMigrationJob job = requireJob(tenantId, actorId, jobId, Action.UPDATE);
        requireExpectedRevision(job, expectedJobRevision);
        if (job.terminal() || job.status() == DatabaseSchemaMigrationJob.Status.READY) {
            throw new IllegalStateException("terminal or ready migrations cannot be cancelled");
        }
        AuditEntry audit = audit(tenantId, actorId, "DATABASE_SCHEMA_MIGRATION_CANCELLED",
                job.id(), job.revision() + 1, "Cancelled staged schema migration");
        if (!migrations.cancel(tenantId, jobId, expectedJobRevision, audit)) {
            throw new IllegalStateException("migration cancellation conflicted with another update");
        }
        return migrations.find(tenantId, jobId)
                .orElseThrow(() -> new IllegalStateException("cancelled migration job disappeared"));
    }

    public DatabaseSchemaMigrationJob get(UUID tenantId, UUID actorId, UUID jobId) {
        return requireJob(tenantId, actorId, jobId, Action.READ);
    }

    private DatabaseSchemaMigrationJob requireJob(UUID tenantId, UUID actorId, UUID jobId, Action action) {
        DatabaseSchemaMigrationJob job = migrations.find(tenantId, jobId)
                .orElseThrow(() -> new IllegalArgumentException("migration job not found"));
        accessPolicy.require(tenantId, actorId, Resource.DATABASE, job.databaseId(), action);
        accessPolicy.require(tenantId, actorId, Resource.DATABASE_TABLE, job.tableId(), action);
        return job;
    }

    private static void requireExpectedRevision(DatabaseSchemaMigrationJob job, long expectedRevision) {
        if (expectedRevision < 1 || job.revision() != expectedRevision) {
            throw new IllegalArgumentException("stale migration job revision; reload before retrying");
        }
    }

    private AuditEntry audit(UUID tenantId, UUID actorId, String action, UUID id, long revision, String summary) {
        return new AuditEntry(tenantId, actorId, action, "DATABASE_SCHEMA_MIGRATION", id, revision, summary, clock.instant());
    }
}
