package tech.kayys.numou.planning.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Immutable, named baseline anchored to an approved progress snapshot. */
public record WorkBreakdownProgressBaseline(
        UUID baselineId, UUID tenantId, UUID structureId, UUID nodeId, UUID sourceSnapshotId,
        String baselineCode, String name, LocalDate effectiveDate, UUID createdBy, Instant createdAt) {
    public WorkBreakdownProgressBaseline {
        Objects.requireNonNull(baselineId, "Baseline id cannot be null");
        Objects.requireNonNull(tenantId, "Tenant id cannot be null");
        Objects.requireNonNull(structureId, "WBS structure id cannot be null");
        Objects.requireNonNull(nodeId, "WBS node id cannot be null");
        Objects.requireNonNull(sourceSnapshotId, "Source snapshot id cannot be null");
        Objects.requireNonNull(baselineCode, "Baseline code cannot be null");
        baselineCode = baselineCode.trim();
        if (baselineCode.isEmpty() || baselineCode.length() > 100)
            throw new IllegalArgumentException("Baseline code must contain 1 to 100 characters");
        Objects.requireNonNull(name, "Baseline name cannot be null");
        name = name.trim();
        if (name.isEmpty() || name.length() > 250)
            throw new IllegalArgumentException("Baseline name must contain 1 to 250 characters");
        Objects.requireNonNull(effectiveDate, "Effective date cannot be null");
        Objects.requireNonNull(createdBy, "Creator id cannot be null");
        Objects.requireNonNull(createdAt, "Creation timestamp cannot be null");
    }
}
