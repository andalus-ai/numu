package tech.kayys.numou.planning.domain;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
/** Compares a frozen approved baseline to a dependency-based current schedule forecast. */
public final class ScheduleVarianceCalculator {
    public ScheduleVarianceReport calculate(UUID tenantId, UUID structureId, PlanId planId, long currentRevision,
            LocalDate reportDate, ApprovedScheduleBaseline baseline, DependencyScheduleForecast forecast) {
        Objects.requireNonNull(tenantId); Objects.requireNonNull(structureId); Objects.requireNonNull(planId);
        Objects.requireNonNull(reportDate); Objects.requireNonNull(baseline); Objects.requireNonNull(forecast);
        if (!tenantId.equals(baseline.tenantId()) || !structureId.equals(baseline.structureId())
                || !planId.equals(baseline.planId()))
            throw new IllegalArgumentException("Baseline belongs to a different tenant, structure, or plan");
        if (currentRevision < 0 || currentRevision < baseline.planRevision())
            throw new IllegalArgumentException("Current revision must be non-negative and not precede baseline");
        if (baseline.approvedAt().atZone(java.time.ZoneOffset.UTC).toLocalDate().isAfter(reportDate))
            throw new IllegalArgumentException("Baseline approval date cannot be after report date");
        List<ScheduleVarianceEntry> entries = new ArrayList<>();
        List<ScheduleVarianceFinding> findings = new ArrayList<>();
        for (Map.Entry<ActivityId, ApprovedScheduleBaseline.ActivityDates> item : baseline.activities().entrySet()) {
            ActivityScheduleForecast current = forecast.activities().get(item.getKey());
            if (current == null) {
                findings.add(new ScheduleVarianceFinding(
                    ScheduleVarianceFinding.Code.BASELINE_ACTIVITY_MISSING_FROM_FORECAST, item.getKey(),
                    "Activity exists in the approved baseline but is absent from the current forecast."));
                continue;
            }
            var dates = item.getValue();
            entries.add(new ScheduleVarianceEntry(item.getKey(), dates.start(), current.earliestStart(),
                    ChronoUnit.DAYS.between(dates.start(), current.earliestStart()), dates.finish(),
                    current.earliestFinish(), ChronoUnit.DAYS.between(dates.finish(), current.earliestFinish()),
                    false, current.critical()));
        }
        for (ActivityId id : forecast.activities().keySet()) if (!baseline.activities().containsKey(id))
            findings.add(new ScheduleVarianceFinding(
                ScheduleVarianceFinding.Code.FORECAST_ACTIVITY_MISSING_FROM_BASELINE, id,
                "Activity exists in the current forecast but is absent from the approved baseline."));
        entries.sort(Comparator.comparing(e -> e.activityId().value().toString()));
        findings.sort(Comparator.comparing((ScheduleVarianceFinding f) ->
                f.activityId() == null ? "" : f.activityId().value().toString()).thenComparing(f -> f.code().name()));
        long variance = ChronoUnit.DAYS.between(baseline.projectFinish(), forecast.forecastFinish());
        return new ScheduleVarianceReport(tenantId, structureId, planId, baseline.baselineId(),
                baseline.planRevision(), currentRevision, reportDate, baseline.projectFinish(),
                forecast.forecastFinish(), variance, entries, findings);
    }
}
