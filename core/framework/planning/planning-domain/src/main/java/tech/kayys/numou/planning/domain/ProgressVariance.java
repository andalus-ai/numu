package tech.kayys.numou.planning.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Comparison between two immutable progress snapshots for the same WBS scope. */
public record ProgressVariance(UUID tenantId, UUID structureId, UUID nodeId,
        UUID baselineSnapshotId, UUID currentSnapshotId, LocalDate baselineDate, LocalDate currentDate,
        BigDecimal baselinePercent, BigDecimal currentPercent, BigDecimal variancePercentagePoints) {
    public ProgressVariance {
        Objects.requireNonNull(tenantId, "Tenant id cannot be null");
        Objects.requireNonNull(structureId, "Structure id cannot be null");
        Objects.requireNonNull(nodeId, "Node id cannot be null");
        Objects.requireNonNull(baselineSnapshotId, "Baseline snapshot id cannot be null");
        Objects.requireNonNull(currentSnapshotId, "Current snapshot id cannot be null");
        Objects.requireNonNull(baselineDate, "Baseline date cannot be null");
        Objects.requireNonNull(currentDate, "Current date cannot be null");
        Objects.requireNonNull(baselinePercent, "Baseline percent cannot be null");
        Objects.requireNonNull(currentPercent, "Current percent cannot be null");
        Objects.requireNonNull(variancePercentagePoints, "Variance cannot be null");
        if (baselineSnapshotId.equals(currentSnapshotId)) throw new IllegalArgumentException("Snapshots must be different");
        if (currentDate.isBefore(baselineDate)) throw new IllegalArgumentException("Current snapshot must not precede baseline");
        if (currentPercent.subtract(baselinePercent).compareTo(variancePercentagePoints) != 0)
            throw new IllegalArgumentException("Variance must equal current minus baseline percentage");
    }
}
