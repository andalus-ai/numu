package tech.kayys.numou.planning.domain;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
/** Auditable baseline-to-forecast comparison for one plan scope and report date. */
public record ScheduleVarianceReport(UUID tenantId, UUID structureId, PlanId planId, UUID baselineId,
        long baselineRevision, long currentRevision, LocalDate reportDate, LocalDate baselineProjectFinish,
        LocalDate forecastProjectFinish, long projectFinishVarianceDays,
        List<ScheduleVarianceEntry> activities, List<ScheduleVarianceFinding> findings) {
    public ScheduleVarianceReport {
        Objects.requireNonNull(tenantId); Objects.requireNonNull(structureId); Objects.requireNonNull(planId);
        Objects.requireNonNull(baselineId); Objects.requireNonNull(reportDate);
        Objects.requireNonNull(baselineProjectFinish); Objects.requireNonNull(forecastProjectFinish);
        activities = List.copyOf(Objects.requireNonNull(activities));
        findings = List.copyOf(Objects.requireNonNull(findings));
        if (baselineRevision < 0 || currentRevision < baselineRevision)
            throw new IllegalArgumentException("Current revision must be at least the baseline revision");
        if (projectFinishVarianceDays != ChronoUnit.DAYS.between(baselineProjectFinish, forecastProjectFinish))
            throw new IllegalArgumentException("Project finish variance must match reported dates");
    }
    public boolean projectFinishIsLate() { return projectFinishVarianceDays > 0; }
    public long lateActivityCount() { return activities.stream().filter(ScheduleVarianceEntry::finishIsLate).count(); }
    public long newlyCriticalActivityCount() { return activities.stream().filter(ScheduleVarianceEntry::newlyCritical).count(); }
}
