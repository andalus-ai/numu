package tech.kayys.numou.planning.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Explicit input to a progress rollup. Weight is supplied by the caller's
 * approved work-breakdown policy; it is never inferred from mixed units.
 */
public record ProgressContribution(
        UUID contributionId,
        UUID tenantId,
        ActivityId activityId,
        LocalDate asOfDate,
        BigDecimal actualPercent,
        BigDecimal weight) {

    public ProgressContribution {
        Objects.requireNonNull(contributionId, "Contribution id cannot be null");
        Objects.requireNonNull(tenantId, "Tenant id cannot be null");
        Objects.requireNonNull(activityId, "Activity id cannot be null");
        Objects.requireNonNull(asOfDate, "As-of date cannot be null");
        Objects.requireNonNull(actualPercent, "Actual percent cannot be null");
        Objects.requireNonNull(weight, "Weight cannot be null");
        if (actualPercent.compareTo(BigDecimal.ZERO) < 0 || actualPercent.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("Actual percent must be between 0 and 100");
        }
        if (weight.signum() <= 0) {
            throw new IllegalArgumentException("Contribution weight must be greater than zero");
        }
    }

    public static ProgressContribution from(UUID contributionId, UUID tenantId,
            ProgressAssessment assessment, BigDecimal weight) {
        Objects.requireNonNull(assessment, "Assessment cannot be null");
        return new ProgressContribution(contributionId, tenantId, assessment.activityId(),
                assessment.asOfDate(), assessment.actualPercent(), weight);
    }
}
