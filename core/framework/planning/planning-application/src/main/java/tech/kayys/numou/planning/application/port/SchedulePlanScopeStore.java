package tech.kayys.numou.planning.application.port;

import java.util.UUID;
import tech.kayys.numou.planning.domain.PlanId;

/**
 * Trusted ownership boundary for assigning a neutral schedule plan to a tenant and structure.
 * Implementations must be idempotent for the same owner and must never silently reassign a plan.
 */
public interface SchedulePlanScopeStore {
    void register(PlanId planId, UUID tenantId, UUID structureId);
}
