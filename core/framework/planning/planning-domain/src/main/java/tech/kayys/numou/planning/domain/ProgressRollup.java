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
 * A deterministic weighted completion summary over an explicitly selected set
 * of work items. The caller selects non-overlapping contributions and supplies
 * comparable weights; this type deliberately does not infer WBS ancestry or
 * combine raw quantities that may have different units.
 */
public record ProgressRollup(
        UUID tenantId,
        LocalDate asOfDate,
        BigDecimal completionPercent,
        BigDecimal totalWeight,
        int includedActivityCount) {

    public ProgressRollup {
        Objects.requireNonNull(tenantId, "Tenant id cannot be null");
        Objects.requireNonNull(asOfDate, "As-of date cannot be null");
        Objects.requireNonNull(completionPercent, "Completion percent cannot be null");
        Objects.requireNonNull(totalWeight, "Total weight cannot be null");
        if (completionPercent.compareTo(BigDecimal.ZERO) < 0
                || completionPercent.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("Completion percent must be between 0 and 100");
        }
        if (totalWeight.signum() <= 0) throw new IllegalArgumentException("Total weight must be positive");
        if (includedActivityCount < 1) throw new IllegalArgumentException("At least one activity is required");
    }

    public static ProgressRollup aggregate(List<ProgressContribution> contributions) {
        Objects.requireNonNull(contributions, "Contributions cannot be null");
        if (contributions.isEmpty()) throw new IllegalArgumentException("Cannot aggregate an empty contribution list");

        UUID tenantId = null;
        LocalDate asOfDate = null;
        BigDecimal weightedCompletion = BigDecimal.ZERO;
        BigDecimal totalWeight = BigDecimal.ZERO;
        Set<UUID> contributionIds = new HashSet<>();
        Set<ActivityId> activityIds = new HashSet<>();

        for (ProgressContribution contribution : List.copyOf(contributions)) {
            Objects.requireNonNull(contribution, "Contribution cannot be null");
            if (!contributionIds.add(contribution.contributionId())) {
                throw new IllegalArgumentException("Duplicate contribution id in rollup");
            }
            if (!activityIds.add(contribution.activityId())) {
                throw new IllegalArgumentException("An activity may contribute only once to a rollup");
            }
            if (tenantId == null) {
                tenantId = contribution.tenantId();
                asOfDate = contribution.asOfDate();
            } else {
                if (!tenantId.equals(contribution.tenantId())) {
                    throw new IllegalArgumentException("A rollup cannot mix tenants");
                }
                if (!asOfDate.equals(contribution.asOfDate())) {
                    throw new IllegalArgumentException("A rollup cannot mix as-of dates");
                }
            }
            weightedCompletion = weightedCompletion.add(
                    contribution.actualPercent().multiply(contribution.weight()));
            totalWeight = totalWeight.add(contribution.weight());
        }

        BigDecimal percent = weightedCompletion.divide(totalWeight, 2, RoundingMode.HALF_UP);
        return new ProgressRollup(tenantId, asOfDate, percent, totalWeight, contributions.size());
    }
}
