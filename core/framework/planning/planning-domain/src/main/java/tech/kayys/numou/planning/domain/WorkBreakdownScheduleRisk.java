package tech.kayys.numou.planning.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Schedule exposure for one WBS node, derived from linked schedule activities. */
public record WorkBreakdownScheduleRisk(
        UUID tenantId, UUID structureId, UUID nodeId, PlanId planId,
        List<ActivityId> activityIds, int criticalActivityCount,
        LocalDate earliestActivityStart, LocalDate forecastActivityFinish,
        Optional<ProgressForecast> progressForecast) {
    public WorkBreakdownScheduleRisk {
        Objects.requireNonNull(tenantId); Objects.requireNonNull(structureId); Objects.requireNonNull(nodeId);
        Objects.requireNonNull(planId);
        activityIds = List.copyOf(Objects.requireNonNull(activityIds));
        progressForecast = Objects.requireNonNull(progressForecast);
        Objects.requireNonNull(earliestActivityStart); Objects.requireNonNull(forecastActivityFinish);
        if (activityIds.isEmpty()) throw new IllegalArgumentException("At least one activity must be linked to the WBS node");
        if (criticalActivityCount < 0 || criticalActivityCount > activityIds.size())
            throw new IllegalArgumentException("Critical activity count is outside linked activity count");
        if (forecastActivityFinish.isBefore(earliestActivityStart))
            throw new IllegalArgumentException("Forecast finish cannot precede earliest activity start");
        if (progressForecast.isPresent()) {
            ProgressForecast forecast = progressForecast.get();
            if (!tenantId.equals(forecast.tenantId()) || !structureId.equals(forecast.structureId())
                    || !nodeId.equals(forecast.nodeId()))
                throw new IllegalArgumentException("Progress forecast scope does not match WBS schedule risk");
        }
    }

    public boolean hasCriticalExposure() { return criticalActivityCount > 0; }
    public boolean progressForecastIsLate() { return progressForecast.map(ProgressForecast::isForecastLate).orElse(false); }
}
