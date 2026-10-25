package tech.kayys.numou.workspace.domain;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Non-mutating summary of saved-view compatibility for one table schema version. */
public record DatabaseTableViewImpactReport(
        UUID tableId,
        long schemaVersion,
        List<DatabaseViewValidationResult> views,
        int validViewCount,
        int invalidViewCount) {
    public DatabaseTableViewImpactReport {
        Objects.requireNonNull(tableId, "tableId");
        if (schemaVersion < 0) throw new IllegalArgumentException("schemaVersion cannot be negative");
        views = views == null ? List.of() : List.copyOf(views);
        if (validViewCount < 0 || invalidViewCount < 0 || validViewCount + invalidViewCount != views.size()) {
            throw new IllegalArgumentException("view counts must be non-negative and match the report entries");
        }
        if (views.stream().anyMatch(view -> !tableId.equals(view.tableId()))) {
            throw new IllegalArgumentException("all validation results must belong to the reported table");
        }
    }

    public int totalViewCount() { return views.size(); }
    public boolean hasIncompatibleViews() { return invalidViewCount > 0; }
}
