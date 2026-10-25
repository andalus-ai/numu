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
}
