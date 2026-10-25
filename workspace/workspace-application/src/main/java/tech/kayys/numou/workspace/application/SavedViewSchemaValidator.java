package tech.kayys.numou.workspace.application;

import tech.kayys.numou.workspace.application.WorkspacePorts.AccessPolicy;
import tech.kayys.numou.workspace.application.WorkspacePorts.Action;
import tech.kayys.numou.workspace.application.WorkspacePorts.Resource;
import tech.kayys.numou.workspace.application.WorkspacePorts.StructuredDatabaseRepository;
import tech.kayys.numou.workspace.domain.DatabaseProperty;
import tech.kayys.numou.workspace.domain.DatabasePropertyType;
import tech.kayys.numou.workspace.domain.DatabaseTable;
import tech.kayys.numou.workspace.domain.DatabaseView;
import tech.kayys.numou.workspace.domain.DatabaseViewType;
import tech.kayys.numou.workspace.domain.DatabaseViewValidationIssue;
import tech.kayys.numou.workspace.domain.DatabaseViewValidationIssue.Code;
import tech.kayys.numou.workspace.domain.DatabaseViewValidationResult;

import java.util.ArrayList;
import java.util.UUID;

/** Diagnoses saved-view drift without deleting or rewriting user configuration. */
public final class SavedViewSchemaValidator {
    private final StructuredDatabaseRepository repository;
    private final AccessPolicy accessPolicy;

    public SavedViewSchemaValidator(StructuredDatabaseRepository repository, AccessPolicy accessPolicy) {
        this.repository = java.util.Objects.requireNonNull(repository, "repository");
        this.accessPolicy = java.util.Objects.requireNonNull(accessPolicy, "accessPolicy");
    }

    public DatabaseViewValidationResult validate(UUID tenantId, UUID actorId, UUID databaseId, UUID viewId) {
        accessPolicy.require(tenantId, actorId, Resource.DATABASE, databaseId, Action.READ);
        DatabaseView view = repository.findView(tenantId, databaseId, viewId)
                .orElseThrow(() -> new IllegalArgumentException("saved view not found"));
        if (!view.createdBy().equals(actorId)) {
            throw new IllegalArgumentException("saved view is personal and belongs to another actor");
        }
        var tableOptional = repository.findTable(tenantId, databaseId, view.tableId());
        if (tableOptional.isEmpty()) {
            return new DatabaseViewValidationResult(view.id(), view.tableId(), 0,
                    java.util.List.of(new DatabaseViewValidationIssue(Code.TABLE_MISSING, null,
                            "The table referenced by this saved view no longer exists.")));
        }
        DatabaseTable table = tableOptional.get();
        accessPolicy.require(tenantId, actorId, Resource.DATABASE_TABLE, table.id(), Action.READ);
        return validateAgainstSchema(view, table);
    }

    /** Validates a view against a candidate schema without persisting either object. */
    public DatabaseViewValidationResult validateAgainstSchema(DatabaseView view, DatabaseTable candidateTable) {
        java.util.Objects.requireNonNull(view, "view");
        java.util.Objects.requireNonNull(candidateTable, "candidateTable");
        if (!view.tableId().equals(candidateTable.id()) || !view.databaseId().equals(candidateTable.databaseId())
                || !view.tenantId().equals(candidateTable.tenantId())) {
            throw new IllegalArgumentException("view and candidate schema must belong to the same tenant table");
        }
        var issues = new ArrayList<DatabaseViewValidationIssue>();
        view.filters().forEach((key, value) -> {
            DatabaseProperty property = findProperty(candidateTable, key);
            if (property == null) {
                issues.add(new DatabaseViewValidationIssue(Code.FILTER_PROPERTY_MISSING, key,
                        "Filter references a property that no longer exists."));
            } else if (value == null) {
                issues.add(new DatabaseViewValidationIssue(Code.FILTER_VALUE_INVALID, key,
                        "Saved filter contains a null value."));
            } else {
                try { property.validateValue(value); }
                catch (IllegalArgumentException invalidValue) {
                    issues.add(new DatabaseViewValidationIssue(Code.FILTER_VALUE_INVALID, key,
                            "Saved filter value is incompatible with the proposed property type or options."));
                }
            }
        });
        view.sorts().forEach(sort -> {
            if (findProperty(candidateTable, sort.propertyKey()) == null) {
                issues.add(new DatabaseViewValidationIssue(Code.SORT_PROPERTY_MISSING, sort.propertyKey(),
                        "Sort references a property that no longer exists."));
            }
        });
        view.visibleProperties().forEach(key -> {
            if (findProperty(candidateTable, key) == null) {
                issues.add(new DatabaseViewValidationIssue(Code.VISIBLE_PROPERTY_MISSING, key,
                        "Visible-property configuration references a property that no longer exists."));
            }
        });
        if (view.groupByProperty() != null) {
            if (view.type() != DatabaseViewType.BOARD) {
                issues.add(new DatabaseViewValidationIssue(Code.GROUP_REQUIRES_BOARD, view.groupByProperty(),
                        "Only BOARD views can use a group-by property."));
            }
            DatabaseProperty grouping = findProperty(candidateTable, view.groupByProperty());
            if (grouping == null) {
                issues.add(new DatabaseViewValidationIssue(Code.GROUP_PROPERTY_MISSING, view.groupByProperty(),
                        "Board grouping references a property that no longer exists."));
            } else if (grouping.type() != DatabasePropertyType.SELECT) {
                issues.add(new DatabaseViewValidationIssue(Code.GROUP_PROPERTY_NOT_SELECT, view.groupByProperty(),
                        "Board grouping requires a SELECT property in the proposed schema."));
            }
        }
        return new DatabaseViewValidationResult(view.id(), view.tableId(), candidateTable.schemaVersion(), issues);
    }

    private static DatabaseProperty findProperty(DatabaseTable table, String key) {
        return table.properties().stream().filter(property -> property.key().equals(key)).findFirst().orElse(null);
    }
}
