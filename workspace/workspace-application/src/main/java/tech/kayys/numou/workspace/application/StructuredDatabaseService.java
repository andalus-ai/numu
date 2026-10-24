package tech.kayys.numou.workspace.application;

import tech.kayys.numou.workspace.application.WorkspacePorts.AccessPolicy;
import tech.kayys.numou.workspace.application.WorkspacePorts.Action;
import tech.kayys.numou.workspace.application.WorkspacePorts.AuditEntry;
import tech.kayys.numou.workspace.application.WorkspacePorts.Resource;
import tech.kayys.numou.workspace.application.WorkspacePorts.StructuredDatabaseRepository;
import tech.kayys.numou.workspace.domain.DatabaseProperty;
import tech.kayys.numou.workspace.domain.DatabaseRecord;
import tech.kayys.numou.workspace.domain.DatabaseRecordPage;
import tech.kayys.numou.workspace.domain.DatabaseTable;
import tech.kayys.numou.workspace.domain.WorkspaceDatabase;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Use cases for neutral workspace databases. Repository adapters must persist each mutation
 * and its audit row atomically, and must implement record revision compare-and-swap.
 */
public final class StructuredDatabaseService {
    private final StructuredDatabaseRepository repository;
    private final AccessPolicy accessPolicy;
    private final Clock clock;

    public StructuredDatabaseService(StructuredDatabaseRepository repository, AccessPolicy accessPolicy, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.accessPolicy = Objects.requireNonNull(accessPolicy, "accessPolicy");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public WorkspaceDatabase createDatabase(UUID tenantId, UUID workspaceId, UUID actorId, String name) {
        accessPolicy.require(tenantId, actorId, Resource.WORKSPACE, workspaceId, Action.UPDATE);
        Instant now = clock.instant();
        WorkspaceDatabase database = WorkspaceDatabase.create(tenantId, workspaceId, actorId, name, now);
        AuditEntry audit = audit(tenantId, actorId, "DATABASE_CREATED", "DATABASE", database.id(), 1,
                "Created workspace database");
        if (!repository.saveDatabase(database, audit)) {
            throw new IllegalStateException("database was not persisted; name may already exist");
        }
        return database;
    }

    public DatabaseTable createTable(UUID tenantId, UUID actorId, UUID databaseId, String name,
                                     List<DatabaseProperty> properties) {
        WorkspaceDatabase database = requireDatabase(tenantId, actorId, databaseId, Action.UPDATE);
        Instant now = clock.instant();
        DatabaseTable table = new DatabaseTable(UUID.randomUUID(), tenantId, database.id(), name, 1, properties);
        AuditEntry audit = audit(tenantId, actorId, "DATABASE_TABLE_CREATED", "DATABASE_TABLE", table.id(), 1,
                "Created table in workspace database");
        if (!repository.saveTable(table, audit)) {
            throw new IllegalStateException("table was not persisted; name may already exist");
        }
        return table;
    }

    public DatabaseRecord createRecord(UUID tenantId, UUID actorId, UUID databaseId, UUID tableId,
                                       Map<String, Object> values) {
        requireDatabase(tenantId, actorId, databaseId, Action.UPDATE);
        DatabaseTable table = repository.findTable(tenantId, databaseId, tableId)
                .orElseThrow(() -> new IllegalArgumentException("table not found in tenant database"));
        accessPolicy.require(tenantId, actorId, Resource.DATABASE_TABLE, tableId, Action.UPDATE);
        DatabaseRecord record = DatabaseRecord.create(tenantId, databaseId, table, values, clock.instant());
        validateRelationTargets(tenantId, actorId, table, record.values());
        AuditEntry audit = audit(tenantId, actorId, "DATABASE_RECORD_CREATED", "DATABASE_RECORD", record.id(),
                record.revision(), "Created structured record");
        if (!repository.saveRecord(record, 0, audit)) {
            throw new IllegalStateException("record was not created; identifier conflict");
        }
        return record;
    }

    public DatabaseRecord updateRecord(UUID tenantId, UUID actorId, UUID recordId, long expectedRevision,
                                       Map<String, Object> values) {
        if (expectedRevision < 1) throw new IllegalArgumentException("expectedRevision must be positive");
        DatabaseRecord current = repository.findRecord(tenantId, recordId)
                .orElseThrow(() -> new IllegalArgumentException("record not found"));
        accessPolicy.require(tenantId, actorId, Resource.DATABASE_RECORD, recordId, Action.UPDATE);
        DatabaseTable table = repository.findTable(tenantId, current.databaseId(), current.tableId())
                .orElseThrow(() -> new IllegalStateException("record schema no longer exists"));
        DatabaseRecord updated = current.update(table, expectedRevision, values, clock.instant());
        validateRelationTargets(tenantId, actorId, table, updated.values());
        AuditEntry audit = audit(tenantId, actorId, "DATABASE_RECORD_UPDATED", "DATABASE_RECORD", recordId,
                updated.revision(), "Updated structured record");
        if (!repository.saveRecord(updated, expectedRevision, audit)) {
            throw new IllegalStateException("record revision conflict or persistence failure");
        }
        return updated;
    }

    /**
     * Lists records with bounded offset pagination and exact-match filters. The query is
     * scoped to a table after workspace/database/table ACL checks; domain aggregates remain
     * authoritative and should not be mirrored here as writable business state.
     */
    public DatabaseRecordPage queryRecords(UUID tenantId, UUID actorId, UUID databaseId, UUID tableId,
                                           Map<String, Object> filters, int limit, int offset) {
        if (limit < 1 || limit > 200) throw new IllegalArgumentException("limit must be between 1 and 200");
        if (offset < 0) throw new IllegalArgumentException("offset cannot be negative");
        requireDatabase(tenantId, actorId, databaseId, Action.READ);
        DatabaseTable table = repository.findTable(tenantId, databaseId, tableId)
                .orElseThrow(() -> new IllegalArgumentException("table not found in tenant database"));
        accessPolicy.require(tenantId, actorId, Resource.DATABASE_TABLE, tableId, Action.READ);
        Map<String, Object> canonicalFilters = new java.util.LinkedHashMap<>();
        if (filters != null) {
            for (Map.Entry<String, Object> entry : filters.entrySet()) {
                DatabaseProperty property = table.properties().stream()
                        .filter(candidate -> candidate.key().equals(entry.getKey())).findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("unknown filter property: " + entry.getKey()));
                if (entry.getValue() == null) throw new IllegalArgumentException("null filter values are not supported");
                canonicalFilters.put(property.key(), property.validateValue(entry.getValue()));
            }
        }
        DatabaseRecordPage page = repository.queryRecords(tenantId, databaseId, tableId,
                Map.copyOf(canonicalFilters), limit, offset);
        // Record-level policies may be stricter than table-level access. Fail closed rather
        // than returning protected rows or exposing a partial page with misleading totals.
        for (DatabaseRecord record : page.records()) {
            accessPolicy.require(tenantId, actorId, Resource.DATABASE_RECORD, record.id(), Action.READ);
        }
        return page;
    }

    /** Creates a personal saved view after validating every referenced property against the current schema. */
    public DatabaseView createView(UUID tenantId, UUID actorId, UUID databaseId, UUID tableId, String name,
                                   DatabaseViewType type, Map<String, Object> filters,
                                   List<DatabaseViewSort> sorts, List<String> visibleProperties) {
        requireDatabase(tenantId, actorId, databaseId, Action.UPDATE);
        DatabaseTable table = repository.findTable(tenantId, databaseId, tableId)
                .orElseThrow(() -> new IllegalArgumentException("table not found in tenant database"));
        accessPolicy.require(tenantId, actorId, Resource.DATABASE_TABLE, tableId, Action.UPDATE);
        Map<String, Object> validatedFilters = new java.util.LinkedHashMap<>();
        if (filters != null) {
            filters.forEach((key, value) -> {
                DatabaseProperty property = table.property(key);
                if (value == null) throw new IllegalArgumentException("null saved-view filters are not supported");
                validatedFilters.put(key, property.validateValue(value));
            });
        }
        List<DatabaseViewSort> validatedSorts = sorts == null ? List.of() : List.copyOf(sorts);
        for (DatabaseViewSort sort : validatedSorts) table.property(sort.propertyKey());
        List<String> visible = visibleProperties == null ? List.of() : List.copyOf(visibleProperties);
        for (String key : visible) table.property(key);
        if (visible.stream().distinct().count() != visible.size()) {
            throw new IllegalArgumentException("visible properties must be unique");
        }
        Instant now = clock.instant();
        DatabaseView view = DatabaseView.create(tenantId, databaseId, tableId, actorId, name, type,
                validatedFilters, validatedSorts, visible, now);
        AuditEntry audit = audit(tenantId, actorId, "DATABASE_VIEW_CREATED", "DATABASE_VIEW", view.id(), 1,
                "Created saved structured database view");
        if (!repository.saveView(view, audit)) {
            throw new IllegalStateException("view was not saved; a view with this name may already exist");
        }
        return view;
    }

    public List<DatabaseView> findViews(UUID tenantId, UUID actorId, UUID databaseId, UUID tableId) {
        requireDatabase(tenantId, actorId, databaseId, Action.READ);
        repository.findTable(tenantId, databaseId, tableId)
                .orElseThrow(() -> new IllegalArgumentException("table not found in tenant database"));
        accessPolicy.require(tenantId, actorId, Resource.DATABASE_TABLE, tableId, Action.READ);
        return repository.findViews(tenantId, databaseId, tableId, actorId);
    }

    public Optional<WorkspaceDatabase> findDatabase(UUID tenantId, UUID actorId, UUID databaseId) {
        WorkspaceDatabase database = requireDatabase(tenantId, actorId, databaseId, Action.READ);
        return Optional.of(database);
    }

    /**
     * Relation fields may only reference readable records in the same database and the schema's
     * declared target table. This keeps generic workspace links from bypassing tenant/resource ACLs.
     */
    private void validateRelationTargets(UUID tenantId, UUID actorId, DatabaseTable sourceTable,
                                         Map<String, Object> values) {
        for (DatabaseProperty property : sourceTable.properties()) {
            if (property.type() != tech.kayys.numou.workspace.domain.DatabasePropertyType.RELATION) continue;
            Object raw = values.get(property.key());
            if (raw == null) continue;
            if (!(raw instanceof List<?> targets)) {
                throw new IllegalArgumentException("relation property '" + property.key() + "' must be a list");
            }
            for (Object targetId : targets) {
                if (!(targetId instanceof UUID id)) {
                    throw new IllegalArgumentException("relation property '" + property.key() + "' must contain record UUIDs");
                }
                DatabaseRecord target = repository.findRecord(tenantId, id)
                        .orElseThrow(() -> new IllegalArgumentException("related record not found or not readable"));
                if (!target.databaseId().equals(sourceTable.databaseId())
                        || !target.tableId().equals(property.relatedTableId())) {
                    throw new IllegalArgumentException("related record does not belong to the configured relation table");
                }
                accessPolicy.require(tenantId, actorId, Resource.DATABASE_RECORD, id, Action.READ);
            }
        }
    }

    private WorkspaceDatabase requireDatabase(UUID tenantId, UUID actorId, UUID databaseId, Action action) {
        WorkspaceDatabase database = repository.findDatabase(tenantId, databaseId)
                .orElseThrow(() -> new IllegalArgumentException("database not found"));
        accessPolicy.require(tenantId, actorId, Resource.DATABASE, databaseId, action);
        return database;
    }

    private AuditEntry audit(UUID tenantId, UUID actorId, String action, String type, UUID id,
                             long revision, String summary) {
        return new AuditEntry(tenantId, actorId, action, type, id, revision, summary, clock.instant());
    }
}
