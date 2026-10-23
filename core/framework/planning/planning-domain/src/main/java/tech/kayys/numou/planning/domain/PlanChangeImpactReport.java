package tech.kayys.numou.planning.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Deterministic impact report comparing two revisions of the same neutral plan. */
public record PlanChangeImpactReport(UUID tenantId, UUID structureId, PlanId planId,
        long previousRevision, long currentRevision, Instant previousCapturedAt, Instant currentCapturedAt,
        LocalDate projectStart, LocalDate previousForecastFinish, LocalDate currentForecastFinish,
        long forecastFinishVarianceDays, List<ActivityId> criticalPathBefore, List<ActivityId> criticalPathAfter,
        List<ActivityId> newlyCriticalActivities, List<ActivityId> noLongerCriticalActivities,
        List<PlanChangeImpactFinding> findings, String methodVersion) {
    public PlanChangeImpactReport {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(structureId);
        Objects.requireNonNull(planId);
        Objects.requireNonNull(previousCapturedAt);
        Objects.requireNonNull(currentCapturedAt);
        Objects.requireNonNull(projectStart);
        Objects.requireNonNull(previousForecastFinish);
        Objects.requireNonNull(currentForecastFinish);
        methodVersion = Objects.requireNonNull(methodVersion).trim();
        criticalPathBefore = List.copyOf(Objects.requireNonNull(criticalPathBefore));
        criticalPathAfter = List.copyOf(Objects.requireNonNull(criticalPathAfter));
        newlyCriticalActivities = List.copyOf(Objects.requireNonNull(newlyCriticalActivities));
        noLongerCriticalActivities = List.copyOf(Objects.requireNonNull(noLongerCriticalActivities));
        findings = List.copyOf(Objects.requireNonNull(findings));
        if (previousRevision < 0 || currentRevision <= previousRevision)
            throw new IllegalArgumentException("Current revision must be greater than previous revision");
        if (methodVersion.isEmpty()) throw new IllegalArgumentException("Method version cannot be blank");
        if (forecastFinishVarianceDays != java.time.temporal.ChronoUnit.DAYS.between(
                previousForecastFinish, currentForecastFinish))
            throw new IllegalArgumentException("Forecast finish variance does not match forecast dates");
    }
}
