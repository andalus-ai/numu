package tech.kayys.numou.planning.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Projects a completion date from historical snapshots using ordinary least-squares
 * regression over progress percentage points against elapsed calendar days.
 *
 * This is a transparent trend estimate, not a probabilistic guarantee. It rejects
 * flat/declining trends and completed work because those cases do not yield a finite
 * remaining-duration forecast under this model.
 */
public final class ProgressForecastCalculator {
    public static final String METHOD_VERSION = "linear-regression-progress-v1";
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    public ProgressForecast calculate(List<WorkBreakdownProgressSnapshot> snapshots, LocalDate plannedFinishDate) {
        Objects.requireNonNull(snapshots, "Snapshots cannot be null");
        Objects.requireNonNull(plannedFinishDate, "Planned finish date cannot be null");
        if (snapshots.size() < 2) throw new IllegalArgumentException("At least two progress snapshots are required");

        List<WorkBreakdownProgressSnapshot> ordered = new ArrayList<>(snapshots);
        ordered.sort(Comparator.comparing(WorkBreakdownProgressSnapshot::asOfDate)
                .thenComparing(WorkBreakdownProgressSnapshot::recordedAt));
        WorkBreakdownProgressSnapshot first = ordered.get(0);
        WorkBreakdownProgressSnapshot latest = ordered.get(ordered.size() - 1);
        for (WorkBreakdownProgressSnapshot snapshot : ordered) {
            if (!snapshot.tenantId().equals(latest.tenantId())
                    || !snapshot.structureId().equals(latest.structureId())
                    || !snapshot.nodeId().equals(latest.nodeId()))
                throw new IllegalArgumentException("All snapshots must belong to the same tenant and WBS scope");
        }
        if (first.asOfDate().equals(latest.asOfDate()))
            throw new IllegalArgumentException("Snapshots must span at least two distinct reporting dates");
        if (latest.completionPercent().compareTo(HUNDRED) >= 0)
            throw new IllegalArgumentException("Completed work has no remaining duration to forecast");

        long origin = first.asOfDate().toEpochDay();
        BigDecimal meanX = BigDecimal.ZERO, meanY = BigDecimal.ZERO;
        List<Long> days = new ArrayList<>(ordered.size());
        for (WorkBreakdownProgressSnapshot snapshot : ordered) {
            long x = snapshot.asOfDate().toEpochDay() - origin;
            days.add(x);
            meanX = meanX.add(BigDecimal.valueOf(x));
            meanY = meanY.add(snapshot.completionPercent());
        }
        meanX = meanX.divide(BigDecimal.valueOf(ordered.size()), 12, RoundingMode.HALF_UP);
        meanY = meanY.divide(BigDecimal.valueOf(ordered.size()), 12, RoundingMode.HALF_UP);
        BigDecimal covariance = BigDecimal.ZERO, varianceX = BigDecimal.ZERO;
        for (int i = 0; i < ordered.size(); i++) {
            BigDecimal dx = BigDecimal.valueOf(days.get(i)).subtract(meanX);
            BigDecimal dy = ordered.get(i).completionPercent().subtract(meanY);
            covariance = covariance.add(dx.multiply(dy));
            varianceX = varianceX.add(dx.multiply(dx));
        }
        if (varianceX.signum() == 0) throw new IllegalArgumentException("Reporting dates must be distinct");
        BigDecimal rate = covariance.divide(varianceX, 12, RoundingMode.HALF_UP);
        if (rate.signum() <= 0)
            throw new IllegalArgumentException("Progress trend must be positive to forecast completion");
        BigDecimal remaining = HUNDRED.subtract(latest.completionPercent());
        long remainingDays = remaining.divide(rate, 0, RoundingMode.CEILING).longValueExact();
        if (remainingDays < 0) throw new IllegalArgumentException("Remaining duration cannot be negative");
        LocalDate forecastDate = latest.asOfDate().plusDays(remainingDays);
        long varianceDays = ChronoUnit.DAYS.between(plannedFinishDate, forecastDate);
        return new ProgressForecast(latest.tenantId(), latest.structureId(), latest.nodeId(),
                latest.snapshotId(), latest.asOfDate(), plannedFinishDate, forecastDate,
                latest.completionPercent(), rate, varianceDays, ordered.size(), METHOD_VERSION);
    }
}
