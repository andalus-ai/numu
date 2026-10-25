package tech.kayys.numou.workspace.domain;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Non-mutating compatibility report for a saved view against the table's current schema. */
public record DatabaseViewValidationResult(
        UUID viewId,
        UUID tableId,
        long schemaVersion,
        List<DatabaseViewValidationIssue> issues) {
    public DatabaseViewValidationResult {
        Objects.requireNonNull(viewId, "viewId");
        Objects.requireNonNull(tableId, "tableId");
        if (schemaVersion < 0) throw new IllegalArgumentException("schemaVersion cannot be negative");
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    public boolean valid() {
        return issues.isEmpty();
    }

    /** Suggested edits for a caller to review; these are guidance, not automatic mutations. */
    public List<DatabaseViewRepairSuggestion> suggestions() {
        return issues.stream().map(issue -> {
            DatabaseViewRepairSuggestion.Action action = switch (issue.code()) {
                case TABLE_MISSING -> DatabaseViewRepairSuggestion.Action.REPAIR_TABLE_REFERENCE;
                case FILTER_PROPERTY_MISSING -> DatabaseViewRepairSuggestion.Action.REMOVE_FILTER;
                case FILTER_VALUE_INVALID -> DatabaseViewRepairSuggestion.Action.REPLACE_FILTER_VALUE;
                case SORT_PROPERTY_MISSING -> DatabaseViewRepairSuggestion.Action.REMOVE_SORT;
                case VISIBLE_PROPERTY_MISSING -> DatabaseViewRepairSuggestion.Action.REMOVE_VISIBLE_PROPERTY;
                case GROUP_PROPERTY_MISSING -> DatabaseViewRepairSuggestion.Action.CLEAR_GROUPING;
                case GROUP_PROPERTY_NOT_SELECT, GROUP_REQUIRES_BOARD ->
                        DatabaseViewRepairSuggestion.Action.CHANGE_GROUPING_PROPERTY;
            };
            return new DatabaseViewRepairSuggestion(issue.code(), issue.propertyKey(), action, issue.message());
        }).toList();
    }
}
