package tech.kayys.numou.workforce.domain.development;

/**
 * Lifecycle states of a {@link WorkerCareerPlan}.
 */
public enum WorkerCareerPlanStatus {
    /** Plan created but not yet activated. */
    DRAFT,
    /** Plan is currently active; career progression is being tracked. */
    ACTIVE,
    /** Worker has achieved the target position; plan is closed successfully. */
    COMPLETED,
    /** Plan was cancelled before completion. */
    CANCELLED
}
