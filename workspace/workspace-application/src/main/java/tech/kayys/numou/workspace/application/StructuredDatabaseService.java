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
import tech.kayys.numou.workspace.domain.DatabaseView;
import tech.kayys.numou.workspace.domain.DatabaseViewSort;
import tech.kayys.numou.workspace.domain.DatabaseViewType;
import tech.kayys.numou.workspace.domain.DatabaseViewValidationResult;
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

    /** Executes a personal saved view using its persisted filters and typed sort specification. */
    public DatabaseRecordPage queryRecordsByView(UUID tenantId, UUID actorId, UUID databaseId, UUID viewId,
                                                  int limit, int offset) {
        if (limit < 1 || limit > 200) throw new IllegalArgumentException("limit must be between 1 and 200");
        if (offset < 0) throw new IllegalArgumentException("offset cannot be negative");
        requireDatabase(tenantId, actorId, databaseId, Action.READ);
        DatabaseView view = repository.findView(tenantId, databaseId, viewId)
                .orElseThrow(() -> new IllegalArgumentException("saved view not found"));
        if (!view.createdBy().equals(actorId)) {
            throw new IllegalArgumentException("saved view is personal and belongs to another actor");
        }
        DatabaseViewValidationResult validation = validateView(tenantId, actorId, databaseId, viewId);
        if (!validation.valid()) {
            throw new IllegalStateException("saved view is incompatible with the current table schema: "
                    + validation.issues().stream()
                    .map(issue -> issue.code() + (issue.propertyKey() == null ? "" : "[" + issue.propertyKey() + "]"))
                    .toList());
        }
        DatabaseTable table = repository.findTable(tenantId, databaseId, view.tableId())
                .orElseThrow(() -> new IllegalStateException("saved view table no longer exists"));
        accessPolicy.require(tenantId, actorId, Resource.DATABASE_TABLE, table.id(), Action.READ);
        Map<String, Object> filters = new java.util.LinkedHashMap<>();
        view.filters().forEach((key, value) -> filters.put(key, table.property(key).validateValue(value)));
        for (DatabaseViewSort sort : view.sorts()) table.property(sort.propertyKey());
        DatabaseRecordPage page = repository.queryRecords(tenantId, databaseId, table.id(),
                Map.copyOf(filters), view.sorts(), limit, offset);
        for (DatabaseRecord record : page.records()) {
            accessPolicy.require(tenantId, actorId, Resource.DATABASE_RECORD, record.id(), Action.READ);
        }
        return page;
    }

    /**
     * Produces a bounded board projection from a saved BOARD view, grouped by a SELECT property.
     * Groups cover the current page only; totalRecords reports the full filtered result count.
     */
    public tech.kayys.numou.workspace.domain.DatabaseRecordBoard queryBoard(UUID tenantId, UUID actorId,
            UUID databaseId, UUID viewId, int limit, int offset) {
        requireDatabase(tenantId, actorId, databaseId, Action.READ);
        DatabaseView view = repository.findView(tenantId, databaseId, viewId)
                .orElseThrow(() -> new IllegalArgumentException("saved view not found"));
        if (view.groupByProperty() == null) {
            throw new IllegalArgumentException("saved board view has no group-by property configured");
        }
        return queryBoard(tenantId, actorId, databaseId, viewId, view.groupByProperty(), limit, offset);
    }

    /** Compatibility overload; persisted grouping cannot be overridden by a query caller. */
    public tech.kayys.numou.workspace.domain.DatabaseRecordBoard queryBoard(UUID tenantId, UUID actorId,
            UUID databaseId, UUID viewId, String groupByProperty, int limit, int offset) {
        requireDatabase(tenantId, actorId, databaseId, Action.READ);
        DatabaseView view = repository.findView(tenantId, databaseId, viewId)
                .orElseThrow(() -> new IllegalArgumentException("saved view not found"));
        if (view.type() != DatabaseViewType.BOARD) {
            throw new IllegalArgumentException("saved view is not a BOARD view");
        }
        if (view.groupByProperty() != null && !view.groupByProperty().equals(groupByProperty)) {
            throw new IllegalArgumentException("group-by property differs from saved view configuration");
        }
        DatabaseRecordPage page = queryRecordsByView(tenantId, actorId, databaseId, viewId, limit, offset);
        DatabaseTable table = repository.findTable(tenantId, databaseId, view.tableId())
                .orElseThrow(() -> new IllegalStateException("saved view table no longer exists"));
        DatabaseProperty groupProperty = table.property(groupByProperty);
        if (groupProperty.type() != tech.kayys.numou.workspace.domain.DatabasePropertyType.SELECT) {
            throw new IllegalArgumentException("board grouping requires a SELECT property");
        }
        Map<String, List<DatabaseRecord>> grouped = new java.util.TreeMap<>();
        List<DatabaseRecord> unassigned = new java.util.ArrayList<>();
        for (DatabaseRecord record : page.records()) {
            Object raw = record.values().get(groupProperty.key());
            if (raw == null) unassigned.add(record);
            else if (raw instanceof String option && groupProperty.options().contains(option)) {
                grouped.computeIfAbsent(option, ignored -> new java.util.ArrayList<>()).add(record);
            } else {
                throw new IllegalStateException("record contains an invalid board grouping value");
            }
        }
        List<tech.kayys.numou.workspace.domain.DatabaseRecordGroup> groups = new java.util.ArrayList<>();
        for (String option : groupProperty.options()) {
            groups.add(new tech.kayys.numou.workspace.domain.DatabaseRecordGroup(option,
                    grouped.getOrDefault(option, List.of())));
        }
        if (!unassigned.isEmpty()) {
            groups.add(new tech.kayys.numou.workspace.domain.DatabaseRecordGroup(null, unassigned));
        }
        return new tech.kayys.numou.workspace.domain.DatabaseRecordBoard(groupProperty.key(), groups,
                page.total(), page.limit(), page.offset());
    }

    /**
     * Validates every saved view owned by the actor for a table and returns a non-mutating impact report.
     * Personal-view filtering is delegated to the repository port; no other actor's view metadata is exposed.
     */
    public tech.kayys.numou.workspace.domain.DatabaseTableViewImpactReport validateViewsForTable(
            UUID tenantId, UUID actorId, UUID databaseId, UUID tableId) {
        List<DatabaseView> views = findViews(tenantId, actorId, databaseId, tableId);
        List<DatabaseViewValidationResult> results = views.stream()
                .map(view -> validateView(tenantId, actorId, databaseId, view.id()))
                .toList();
        long valid = results.stream().filter(DatabaseViewValidationResult::valid).count();
        return new tech.kayys.numou.workspace.domain.DatabaseTableViewImpactReport(
                tableId, results.isEmpty() ? repository.findTable(tenantId, databaseId, tableId)
                        .orElseThrow(() -> new IllegalArgumentException("table not found in tenant database")).schemaVersion()
                        : results.get(0).schemaVersion(), results, (int) valid, results.size() - (int) valid);
    }

    /**
     * Previews a proposed table schema against all of the actor's saved views. The expected version
     * prevents callers from reviewing a candidate based on a schema that has already changed.
     * This operation is read-only; applying the schema remains a separate, version-checked operation.
     */
    public tech.kayys.numou.workspace.domain.DatabaseSchemaChangePreview previewSchemaChange(
            UUID tenantId, UUID actorId, UUID databaseId, UUID tableId, long expectedSchemaVersion,
            String proposedName, List<DatabaseProperty> proposedProperties) {
        requireDatabase(tenantId, actorId, databaseId, Action.UPDATE);
        DatabaseTable current = repository.findTable(tenantId, databaseId, tableId)
                .orElseThrow(() -> new IllegalArgumentException("table not found in tenant database"));
        accessPolicy.require(tenantId, actorId, Resource.DATABASE_TABLE, tableId, Action.UPDATE);
        if (expectedSchemaVersion < 1 || current.schemaVersion() != expectedSchemaVersion) {
            throw new IllegalArgumentException("stale table schema version; refresh before previewing changes");
        }
        DatabaseTable candidate = new DatabaseTable(current.id(), current.tenantId(), current.databaseId(),
                proposedName == null || proposedName.isBlank() ? current.name() : proposedName,
                current.schemaVersion() + 1, proposedProperties);
        SavedViewSchemaValidator validator = new SavedViewSchemaValidator(repository, accessPolicy);
        List<DatabaseViewValidationResult> results = findViews(tenantId, actorId, databaseId, tableId).stream()
                .map(view -> validator.validateAgainstSchema(view, candidate)).toList();
        int compatible = (int) results.stream().filter(DatabaseViewValidationResult::valid).count();
        return new tech.kayys.numou.workspace.domain.DatabaseSchemaChangePreview(tableId, current.schemaVersion(),
                candidate.schemaVersion(), results, compatible, results.size() - compatible);
    }

    /** Reports schema drift without mutating or deleting the saved view. */
    public DatabaseViewValidationResult validateView(UUID tenantId, UUID actorId, UUID databaseId, UUID viewId) {
        return new SavedViewSchemaValidator(repository, accessPolicy).validate(tenantId, actorId, databaseId, viewId);
    }

    /** Creates a personal saved view after validating every referenced property against the current schema. */
    public DatabaseView createView(UUID tenantId, UUID actorId, UUID databaseId, UUID tableId, String name,
                                   DatabaseViewType type, Map<String, Object> filters,
                                   List<DatabaseViewSort> sorts, List<String> visibleProperties) {
        return createView(tenantId, actorId, databaseId, tableId, name, type, filters, sorts,
                visibleProperties, null);
    }

    /** Creates a saved view with optional persistent board grouping configuration. */
    public DatabaseView createView(UUID tenantId, UUID actorId, UUID databaseId, UUID tableId, String name,
                                   DatabaseViewType type, Map<String, Object> filters,
                                   List<DatabaseViewSort> sorts, List<String> visibleProperties,
                                   String groupByProperty) {
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
        if (groupByProperty != null) {
            if (type != DatabaseViewType.BOARD) {
                throw new IllegalArgumentException("only BOARD views can define a group-by property");
            }
            DatabaseProperty grouping = table.property(groupByProperty);
            if (grouping.type() != tech.kayys.numou.workspace.domain.DatabasePropertyType.SELECT) {
                throw new IllegalArgumentException("board grouping requires a SELECT property");
            }
        }
        if (visible.stream().distinct().count() != visible.size()) {
            throw new IllegalArgumentException("visible properties must be unique");
        }
        Instant now = clock.instant();
        DatabaseView view = DatabaseView.create(tenantId, databaseId, tableId, actorId, name, type,
                validatedFilters, validatedSorts, visible, groupByProperty, now);
        AuditEntry audit = audit(tenantId, actorId, "DATABASE_VIEW_CREATED", "DATABASE_VIEW", view.id(), 1,
                "Created saved structured database view");
        if (!repository.saveView(view, audit)) {
            throw new IllegalStateException("view was not saved; a view with this name may already exist");
        }
        return view;
    }

    /** Updates a personal saved view using revision compare-and-swap to prevent lost updates. */
    public DatabaseView updateView(UUID tenantId, UUID actorId, UUID databaseId, UUID viewId,
                                   long expectedRevision, String name, DatabaseViewType type,
                                   Map<String, Object> filters, List<DatabaseViewSort> sorts,
                                   List<String> visibleProperties, String groupByProperty) {
        requireDatabase(tenantId, actorId, databaseId, Action.UPDATE);
        DatabaseView current = repository.findView(tenantId, databaseId, viewId)
                .orElseThrow(() -> new IllegalArgumentException("saved view not found"));
        if (!current.createdBy().equals(actorId)) {
            throw new IllegalArgumentException("saved view is personal and belongs to another actor");
        }
        if (current.revision() != expectedRevision) {
            throw new IllegalArgumentException("stale saved-view revision");
        }
        DatabaseTable table = repository.findTable(tenantId, databaseId, current.tableId())
                .orElseThrow(() -> new IllegalArgumentException("table not found in tenant database"));
        accessPolicy.require(tenantId, actorId, Resource.DATABASE_TABLE, current.tableId(), Action.UPDATE);
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
        if (groupByProperty != null) {
            if (type != DatabaseViewType.BOARD) {
                throw new IllegalArgumentException("only BOARD views can define a group-by property");
            }
            DatabaseProperty grouping = table.property(groupByProperty);
            if (grouping.type() != tech.kayys.numou.workspace.domain.DatabasePropertyType.SELECT) {
                throw new IllegalArgumentException("board grouping requires a SELECT property");
            }
        }
        DatabaseView updated = current.revise(name, type, validatedFilters, validatedSorts, visible,
                groupByProperty, expectedRevision, clock.instant());
        AuditEntry audit = audit(tenantId, actorId, "DATABASE_VIEW_UPDATED", "DATABASE_VIEW", viewId,
                updated.revision(), "Updated personal structured database view");
        if (!repository.updateView(updated, expectedRevision, audit)) {
            throw new IllegalStateException("saved view update conflicted with another update or duplicate name");
        }
        return updated;
    }

    /**
     * Repairs a schema-incompatible personal view through the same revision/CAS and audit path as updates.
     * The caller supplies the full replacement configuration so ambiguous changes are never guessed.
     */
    public DatabaseView repairView(UUID tenantId, UUID actorId, UUID databaseId, UUID viewId,
                                   long expectedRevision, String name, DatabaseViewType type,
                                   Map<String, Object> filters, List<DatabaseViewSort> sorts,
                                   List<String> visibleProperties, String groupByProperty) {
        DatabaseView current = repository.findView(tenantId, databaseId, viewId)
                .orElseThrow(() -> new IllegalArgumentException("saved view not found"));
        if (!current.createdBy().equals(actorId)) {
            throw new IllegalArgumentException("saved view is personal and belongs to another actor");
        }
        if (current.revision() != expectedRevision) {
            throw new IllegalArgumentException("stale saved-view revision");
        }
        DatabaseViewValidationResult validation = validateView(tenantId, actorId, databaseId, viewId);
        if (validation.valid()) {
            throw new IllegalArgumentException("saved view is already compatible; use updateView for ordinary edits");
        }
        // updateView validates every replacement field against the current schema and writes the
        // revision increment and audit event atomically through repository compare-and-swap.
        return updateView(tenantId, actorId, databaseId, viewId, expectedRevision, name, type,
                filters, sorts, visibleProperties, groupByProperty);
    }

    /** Deletes only the actor's own personal view; deletion and audit are one repository transaction. */
    public void deleteView(UUID tenantId, UUID actorId, UUID databaseId, UUID viewId) {
        requireDatabase(tenantId, actorId, databaseId, Action.UPDATE);
        DatabaseView view = repository.findView(tenantId, databaseId, viewId)
                .orElseThrow(() -> new IllegalArgumentException("saved view not found"));
        if (!view.createdBy().equals(actorId)) {
            throw new IllegalArgumentException("saved view is personal and belongs to another actor");
        }
        AuditEntry audit = audit(tenantId, actorId, "DATABASE_VIEW_DELETED", "DATABASE_VIEW", viewId, view.revision(),
                "Deleted personal structured database view");
        if (!repository.deleteView(tenantId, databaseId, viewId, actorId, view.revision(), audit)) {
            throw new IllegalStateException("saved view was not deleted");
        }
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
