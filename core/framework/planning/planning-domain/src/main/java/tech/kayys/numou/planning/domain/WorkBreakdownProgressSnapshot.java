package tech.kayys.numou.planning.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Immutable audit record of a WBS progress calculation and its frozen inputs. */
public record WorkBreakdownProgressSnapshot(
        UUID snapshotId, UUID tenantId, UUID structureId, UUID nodeId, UUID planId,
        long planRevision, LocalDate asOfDate, String weightingPolicyVersion,
        BigDecimal completionPercent, BigDecimal totalWeight, int includedActivityCount,
        Instant recordedAt, List<ActivityContribution> contributions) {
    public WorkBreakdownProgressSnapshot {
        Objects.requireNonNull(snapshotId, "Snapshot id cannot be null");
        Objects.requireNonNull(tenantId, "Tenant id cannot be null");
        Objects.requireNonNull(structureId, "Structure id cannot be null");
        Objects.requireNonNull(nodeId, "Node id cannot be null");
        Objects.requireNonNull(planId, "Plan id cannot be null");
        if (planRevision < 0) throw new IllegalArgumentException("Plan revision cannot be negative");
        Objects.requireNonNull(asOfDate, "As-of date cannot be null");
        Objects.requireNonNull(weightingPolicyVersion, "Weighting policy version cannot be null");
        weightingPolicyVersion = weightingPolicyVersion.trim();
        if (weightingPolicyVersion.isEmpty()) throw new IllegalArgumentException("Weighting policy version cannot be blank");
        Objects.requireNonNull(completionPercent, "Completion percent cannot be null");
        Objects.requireNonNull(totalWeight, "Total weight cannot be null");
        Objects.requireNonNull(recordedAt, "Recorded-at timestamp cannot be null");
        if (completionPercent.compareTo(BigDecimal.ZERO) < 0 || completionPercent.compareTo(new BigDecimal("100")) > 0)
            throw new IllegalArgumentException("Completion percent must be between 0 and 100");
        if (totalWeight.signum() <= 0) throw new IllegalArgumentException("Total weight must be positive");
        if (includedActivityCount < 1) throw new IllegalArgumentException("At least one activity must be included");
        contributions = List.copyOf(Objects.requireNonNull(contributions, "Contributions cannot be null"));
        if (contributions.size() != includedActivityCount)
            throw new IllegalArgumentException("Contribution count must match included activity count");
        Set<UUID> activityIds = new HashSet<>();
        BigDecimal calculatedWeight = BigDecimal.ZERO;
        BigDecimal weightedCompletion = BigDecimal.ZERO;
        for (ActivityContribution contribution : contributions) {
            Objects.requireNonNull(contribution, "Contribution cannot be null");
            if (!activityIds.add(contribution.activityId()))
                throw new IllegalArgumentException("Activity can appear only once in a snapshot");
            calculatedWeight = calculatedWeight.add(contribution.weight());
            weightedCompletion = weightedCompletion.add(contribution.actualPercent().multiply(contribution.weight()));
        }
        if (calculatedWeight.compareTo(totalWeight) != 0)
            throw new IllegalArgumentException("Snapshot total weight does not match its contributions");
        BigDecimal calculatedPercent = weightedCompletion.divide(totalWeight, 2, java.math.RoundingMode.HALF_UP);
        if (calculatedPercent.compareTo(completionPercent) != 0)
            throw new IllegalArgumentException("Snapshot completion does not match its contributions");
    }

    /** Frozen input for one activity in the rollup. */
    public record ActivityContribution(UUID activityId, BigDecimal actualPercent, BigDecimal weight) {
        public ActivityContribution {
            Objects.requireNonNull(activityId, "Activity id cannot be null");
            Objects.requireNonNull(actualPercent, "Actual percent cannot be null");
            Objects.requireNonNull(weight, "Weight cannot be null");
            if (actualPercent.compareTo(BigDecimal.ZERO) < 0 || actualPercent.compareTo(new BigDecimal("100")) > 0)
                throw new IllegalArgumentException("Actual percent must be between 0 and 100");
            if (weight.signum() <= 0) throw new IllegalArgumentException("Weight must be positive");
        }
    }
}
