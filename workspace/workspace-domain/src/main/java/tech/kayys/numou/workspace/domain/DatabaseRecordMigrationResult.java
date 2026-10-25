package tech.kayys.numou.workspace.domain;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Validation result for one active record in an explicit schema migration plan. */
public record DatabaseRecordMigrationResult(UUID recordId, long currentRevision, List<String> issues) {
    public DatabaseRecordMigrationResult {
        Objects.requireNonNull(recordId, "recordId");
        if (currentRevision < 1) throw new IllegalArgumentException("currentRevision must be positive");
        issues = issues == null ? List.of() : List.copyOf(issues);
    }
    public boolean valid() { return issues.isEmpty(); }
}
