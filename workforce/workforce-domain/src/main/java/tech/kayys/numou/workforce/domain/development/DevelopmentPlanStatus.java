package tech.kayys.numou.workforce.domain.development;

/**
 * Lifecycle states of a {@link DevelopmentPlan}.
 */
public enum DevelopmentPlanStatus {
    /** Plan created but not yet activated. */
    DRAFT,
    /** Plan is currently active and being worked on. */
    ACTIVE,
    /** All development needs have been addressed; plan is closed successfully. */
    COMPLETED,
    /** Plan was cancelled before completion. */
    CANCELLED
}
