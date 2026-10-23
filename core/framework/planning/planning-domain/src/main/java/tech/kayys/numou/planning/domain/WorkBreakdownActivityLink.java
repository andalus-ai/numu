package tech.kayys.numou.planning.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Tenant-scoped association between a schedule activity and a node in an
 * immutable WBS snapshot. The association does not imply a quantity unit,
 * cost account, or domain-specific ownership rule.
 */
public record WorkBreakdownActivityLink(
        UUID tenantId,
        UUID structureId,
        UUID nodeId,
        PlanId planId,
        ActivityId activityId) {
    public WorkBreakdownActivityLink {
        Objects.requireNonNull(tenantId, "Tenant id cannot be null");
        Objects.requireNonNull(structureId, "Structure id cannot be null");
        Objects.requireNonNull(nodeId, "Node id cannot be null");
        Objects.requireNonNull(planId, "Plan id cannot be null");
        Objects.requireNonNull(activityId, "Activity id cannot be null");
    }
}
