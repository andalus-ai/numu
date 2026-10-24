package tech.kayys.numou.planning.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Domain-neutral project controls view joining schedule forecast variance with
 * approved WBS progress variance for a single reporting date.
 *
 * This report intentionally does not infer cost, budget, or earned value. Those
 * require an explicit cost model and approved cost evidence; schedule dates and
 * completion percentages alone are not sufficient to calculate them.
 */
public record ProjectControlsReport(
        UUID tenantId,
        UUID structureId,
        PlanId planId,
        LocalDate reportDate,
        ScheduleVarianceReport scheduleVariance,
        List<ProgressVariance> progressVariances,
        BigDecimal averageProgressVariancePercentagePoints,
        int behindBaselineNodeCount,
        int aheadOfBaselineNodeCount,
        int onBaselineNodeCount) {

    public ProjectControlsReport {
        Objects.requireNonNull(tenantId, "Tenant id cannot be null");
        Objects.requireNonNull(structureId, "Structure id cannot be null");
        Objects.requireNonNull(planId, "Plan id cannot be null");
        Objects.requireNonNull(reportDate, "Report date cannot be null");
        Objects.requireNonNull(scheduleVariance, "Schedule variance report cannot be null");
        Objects.requireNonNull(progressVariances, "Progress variances cannot be null");
        progressVariances = List.copyOf(progressVariances);

        if (!tenantId.equals(scheduleVariance.tenantId())
                || !structureId.equals(scheduleVariance.structureId())
                || !planId.equals(scheduleVariance.planId())
                || !reportDate.equals(scheduleVariance.reportDate())) {
            throw new IllegalArgumentException("Schedule report must match project controls tenant, structure, plan and date");
        }

        Set<String> scopes = new HashSet<>();
        BigDecimal totalVariance = BigDecimal.ZERO;
        int behind = 0;
        int ahead = 0;
        int on = 0;
        for (ProgressVariance variance : progressVariances) {
            Objects.requireNonNull(variance, "Progress variance cannot be null");
            if (!tenantId.equals(variance.tenantId()) || !structureId.equals(variance.structureId())) {
                throw new IllegalArgumentException("Progress variances must match project controls tenant and structure");
            }
            if (!reportDate.equals(variance.currentDate())) {
                throw new IllegalArgumentException("Progress variances must use the project controls report date");
            }
            String scope = variance.nodeId().toString();
            if (!scopes.add(scope)) {
                throw new IllegalArgumentException("Only one progress variance per WBS node is allowed");
            }
            int sign = variance.variancePercentagePoints().compareTo(BigDecimal.ZERO);
            if (sign < 0) behind++;
            else if (sign > 0) ahead++;
            else on++;
            totalVariance = totalVariance.add(variance.variancePercentagePoints());
        }

        BigDecimal expectedAverage = progressVariances.isEmpty()
                ? BigDecimal.ZERO.setScale(2)
                : totalVariance.divide(BigDecimal.valueOf(progressVariances.size()), 2, RoundingMode.HALF_UP);
        if (averageProgressVariancePercentagePoints == null
                || averageProgressVariancePercentagePoints.compareTo(expectedAverage) != 0) {
            throw new IllegalArgumentException("Average progress variance must match included node variances");
        }
        if (behindBaselineNodeCount != behind || aheadOfBaselineNodeCount != ahead || onBaselineNodeCount != on) {
            throw new IllegalArgumentException("Progress variance counts must match included node variances");
        }
    }

    public static ProjectControlsReport of(
            UUID tenantId,
            UUID structureId,
            PlanId planId,
            LocalDate reportDate,
            ScheduleVarianceReport scheduleVariance,
            List<ProgressVariance> progressVariances) {
        Objects.requireNonNull(progressVariances, "Progress variances cannot be null");
        List<ProgressVariance> values = List.copyOf(progressVariances);
        BigDecimal total = BigDecimal.ZERO;
        int behind = 0;
        int ahead = 0;
        int on = 0;
        for (ProgressVariance variance : values) {
            Objects.requireNonNull(variance, "Progress variance cannot be null");
            total = total.add(variance.variancePercentagePoints());
            int sign = variance.variancePercentagePoints().compareTo(BigDecimal.ZERO);
            if (sign < 0) behind++;
            else if (sign > 0) ahead++;
            else on++;
        }
        BigDecimal average = values.isEmpty() ? BigDecimal.ZERO.setScale(2)
                : total.divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
        return new ProjectControlsReport(tenantId, structureId, planId, reportDate,
                scheduleVariance, values, average, behind, ahead, on);
    }

    public boolean scheduleIsLate() {
        return scheduleVariance.projectFinishIsLate();
    }

    public boolean hasProgressBehindBaseline() {
        return behindBaselineNodeCount > 0;
    }
}
