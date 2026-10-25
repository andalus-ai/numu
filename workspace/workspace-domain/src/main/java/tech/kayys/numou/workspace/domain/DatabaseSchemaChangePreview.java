package tech.kayys.numou.workspace.domain;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Preview of how a proposed table schema affects the caller's saved views; never a mutation command. */
public record DatabaseSchemaChangePreview(
        UUID tableId,
        long currentSchemaVersion,
        long proposedSchemaVersion,
        List<DatabaseViewValidationResult> viewResults,
        int compatibleViewCount,
        int incompatibleViewCount) {
    public DatabaseSchemaChangePreview {
        Objects.requireNonNull(tableId, "tableId");
        if (currentSchemaVersion < 1 || proposedSchemaVersion != currentSchemaVersion + 1) {
            throw new IllegalArgumentException("proposed schema version must immediately follow current version");
        }
        viewResults = viewResults == null ? List.of() : List.copyOf(viewResults);
        if (compatibleViewCount < 0 || incompatibleViewCount < 0
                || compatibleViewCount + incompatibleViewCount != viewResults.size()) {
            throw new IllegalArgumentException("view counts must match the validation results");
        }
        if (viewResults.stream().anyMatch(result -> !tableId.equals(result.tableId())
                || result.schemaVersion() != proposedSchemaVersion)) {
            throw new IllegalArgumentException("all results must target this table's proposed schema version");
        }
    }
    public int totalViewCount() { return viewResults.size(); }
    public boolean hasIncompatibleViews() { return incompatibleViewCount > 0; }
}
