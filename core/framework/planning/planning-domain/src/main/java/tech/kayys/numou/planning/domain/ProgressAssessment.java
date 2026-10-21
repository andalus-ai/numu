package tech.kayys.numou.planning.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/** Pure calculation comparing a dated actual-progress observation with a schedule forecast. */
public record ProgressAssessment(
        ActivityId activityId,
        LocalDate asOfDate,
        BigDecimal plannedPercent,
        BigDecimal actualPercent,
        BigDecimal variancePercentagePoints,
        Status status) {

    public enum Status { NOT_STARTED, ON_TRACK, AHEAD, BEHIND, COMPLETE }

    public ProgressAssessment {
        Objects.requireNonNull(activityId, "Activity id cannot be null");
        Objects.requireNonNull(asOfDate, "As-of date cannot be null");
        Objects.requireNonNull(plannedPercent, "Planned percent cannot be null");
        Objects.requireNonNull(actualPercent, "Actual percent cannot be null");
        Objects.requireNonNull(variancePercentagePoints, "Variance cannot be null");
        Objects.requireNonNull(status, "Status cannot be null");
    }

    public static ProgressAssessment assess(
            ScheduleActivity activity, LocalDate asOfDate, BigDecimal actualPercent) {
        Objects.requireNonNull(activity, "Activity cannot be null");
        Objects.requireNonNull(asOfDate, "As-of date cannot be null");
        Objects.requireNonNull(actualPercent, "Actual percent cannot be null");
        if (actualPercent.compareTo(BigDecimal.ZERO) < 0
                || actualPercent.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("Actual percent must be between 0 and 100");
        }

        BigDecimal planned = plannedPercent(activity, asOfDate);
        BigDecimal actual = actualPercent.setScale(2, RoundingMode.HALF_UP);
        BigDecimal variance = actual.subtract(planned).setScale(2, RoundingMode.HALF_UP);
        Status status;
        if (actual.compareTo(new BigDecimal("100")) >= 0) status = Status.COMPLETE;
        else if (asOfDate.isBefore(activity.plannedStart())) status = Status.NOT_STARTED;
        else if (variance.compareTo(BigDecimal.ZERO) > 0) status = Status.AHEAD;
        else if (variance.compareTo(BigDecimal.ZERO) < 0) status = Status.BEHIND;
        else status = Status.ON_TRACK;
        return new ProgressAssessment(activity.id(), asOfDate, planned, actual, variance, status);
    }

    /**
     * Linear time-phased plan using inclusive calendar dates. This is a transparent
     * default, not earned-value weighting; callers may provide a richer policy later.
     */
    public static BigDecimal plannedPercent(ScheduleActivity activity, LocalDate asOfDate) {
        Objects.requireNonNull(activity, "Activity cannot be null");
        Objects.requireNonNull(asOfDate, "As-of date cannot be null");
        if (asOfDate.isBefore(activity.plannedStart())) return new BigDecimal("0.00");
        if (!asOfDate.isBefore(activity.plannedFinish())) return new BigDecimal("100.00");
        long totalDays = ChronoUnit.DAYS.between(activity.plannedStart(), activity.plannedFinish()) + 1;
        long elapsedDays = ChronoUnit.DAYS.between(activity.plannedStart(), asOfDate) + 1;
        return BigDecimal.valueOf(elapsedDays).multiply(new BigDecimal("100"))
                .divide(BigDecimal.valueOf(totalDays), 2, RoundingMode.HALF_UP);
    }
}
