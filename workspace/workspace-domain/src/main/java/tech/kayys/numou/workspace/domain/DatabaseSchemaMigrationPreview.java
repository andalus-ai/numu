package tech.kayys.numou.workspace.domain;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Non-mutating impact report for an explicit schema migration. Record values are deliberately
 * omitted; callers receive only validation diagnostics and saved-view compatibility results.
 */
public record DatabaseSchemaMigrationPreview(
        UUID tableId,
        long currentSchemaVersion,
        long proposedSchemaVersion,
        int activeRecordCount,
        List<DatabaseRecordMigrationResult> recordResults,
        List<DatabaseViewValidationResult> viewResults,
        List<String> planIssues) {
    public DatabaseSchemaMigrationPreview {
        Objects.requireNonNull(tableId, "tableId");
        if (currentSchemaVersion < 1 || proposedSchemaVersion != currentSchemaVersion + 1) {
            throw new IllegalArgumentException("proposed schema version must immediately follow current version");
        }
        if (activeRecordCount < 0) throw new IllegalArgumentException("activeRecordCount cannot be negative");
        recordResults = recordResults == null ? List.of() : List.copyOf(recordResults);
        viewResults = viewResults == null ? List.of() : List.copyOf(viewResults);
        planIssues = planIssues == null ? List.of() : List.copyOf(planIssues);
        if (recordResults.size() != activeRecordCount) {
            throw new IllegalArgumentException("every active record must have exactly one migration result");
        }
        if (viewResults.stream().anyMatch(result -> !tableId.equals(result.tableId())
                || result.schemaVersion() != proposedSchemaVersion)) {
            throw new IllegalArgumentException("view results must target the proposed schema");
        }
    }
    public int validRecordCount() { return (int) recordResults.stream().filter(DatabaseRecordMigrationResult::valid).count(); }
    public int invalidRecordCount() { return activeRecordCount - validRecordCount(); }
    public int compatibleViewCount() { return (int) viewResults.stream().filter(DatabaseViewValidationResult::valid).count(); }
    public int incompatibleViewCount() { return viewResults.size() - compatibleViewCount(); }
    /** Incompatible views are reported but preserved; they can be repaired separately after apply. */
    public boolean readyToApply() { return planIssues.isEmpty() && invalidRecordCount() == 0; }
}
