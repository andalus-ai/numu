package tech.kayys.numou.planning.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Auditable schedule forecast derived from immutable progress snapshots. */
public record ProgressForecast(
        UUID tenantId, UUID structureId, UUID nodeId,
        UUID latestSnapshotId, LocalDate latestAsOfDate,
        LocalDate plannedFinishDate, LocalDate forecastFinishDate,
        BigDecimal latestCompletionPercent, BigDecimal estimatedProgressPointsPerDay,
        long scheduleVarianceDays, int observationsUsed, String methodVersion) {
    public ProgressForecast {
        Objects.requireNonNull(tenantId, "Tenant id cannot be null");
        Objects.requireNonNull(structureId, "Structure id cannot be null");
        Objects.requireNonNull(nodeId, "Node id cannot be null");
        Objects.requireNonNull(latestSnapshotId, "Latest snapshot id cannot be null");
        Objects.requireNonNull(latestAsOfDate, "Latest reporting date cannot be null");
        Objects.requireNonNull(plannedFinishDate, "Planned finish date cannot be null");
        Objects.requireNonNull(forecastFinishDate, "Forecast finish date cannot be null");
        Objects.requireNonNull(latestCompletionPercent, "Completion percent cannot be null");
        Objects.requireNonNull(estimatedProgressPointsPerDay, "Estimated rate cannot be null");
        Objects.requireNonNull(methodVersion, "Method version cannot be null");
        methodVersion = methodVersion.trim();
        if (methodVersion.isEmpty()) throw new IllegalArgumentException("Method version cannot be blank");
        if (latestCompletionPercent.signum() < 0 || latestCompletionPercent.compareTo(new BigDecimal("100")) > 0)
            throw new IllegalArgumentException("Completion must be between 0 and 100");
        if (estimatedProgressPointsPerDay.signum() <= 0)
            throw new IllegalArgumentException("Forecast rate must be positive");
        if (observationsUsed < 2) throw new IllegalArgumentException("At least two observations are required");
        if (scheduleVarianceDays != java.time.temporal.ChronoUnit.DAYS.between(plannedFinishDate, forecastFinishDate))
            throw new IllegalArgumentException("Schedule variance must equal forecast finish minus planned finish");
    }

    /** Negative variance means forecast early; positive means forecast late. */
    public boolean isForecastLate() { return scheduleVarianceDays > 0; }
}
