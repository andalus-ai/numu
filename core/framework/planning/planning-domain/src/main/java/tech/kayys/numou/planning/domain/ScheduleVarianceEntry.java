package tech.kayys.numou.planning.domain;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
/** Date variance for one activity; positive finish variance means later than baseline. */
public record ScheduleVarianceEntry(ActivityId activityId, LocalDate baselineStart, LocalDate forecastStart,
        long startVarianceDays, LocalDate baselineFinish, LocalDate forecastFinish, long finishVarianceDays,
        boolean baselineCritical, boolean forecastCritical) {
    public ScheduleVarianceEntry {
        Objects.requireNonNull(activityId); Objects.requireNonNull(baselineStart); Objects.requireNonNull(forecastStart);
        Objects.requireNonNull(baselineFinish); Objects.requireNonNull(forecastFinish);
        if (finishVarianceDays != ChronoUnit.DAYS.between(baselineFinish, forecastFinish))
            throw new IllegalArgumentException("Finish variance must match dates");
        if (startVarianceDays != ChronoUnit.DAYS.between(baselineStart, forecastStart))
            throw new IllegalArgumentException("Start variance must match dates");
    }
    public boolean finishIsLate() { return finishVarianceDays > 0; }
    public boolean newlyCritical() { return !baselineCritical && forecastCritical; }
    public boolean noLongerCritical() { return baselineCritical && !forecastCritical; }
}
