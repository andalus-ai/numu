package tech.kayys.numou.workforce.domain.analytics;

import tech.kayys.numou.workforce.domain.position.PositionId;

import java.util.Objects;

public record WorkforcePlanItem(
        WorkforcePlanItemId id,
        PositionId positionId,
        String period,
        int requiredHeadcount,
        int targetHeadcount
) {
    public WorkforcePlanItem {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(positionId, "positionId must not be null");
        Objects.requireNonNull(period, "period must not be null");
        if (requiredHeadcount < 0 || targetHeadcount < 0) {
            throw new IllegalArgumentException("Headcount cannot be negative");
        }
    }
}
