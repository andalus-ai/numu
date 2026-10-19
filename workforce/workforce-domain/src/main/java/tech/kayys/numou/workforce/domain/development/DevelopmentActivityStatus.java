package tech.kayys.numou.workforce.domain.development;

/**
 * Lifecycle states of a {@link DevelopmentActivity}.
 */
public enum DevelopmentActivityStatus {
    /** Activity has been planned but not yet started. */
    PLANNED,
    /** Activity is currently in progress. */
    IN_PROGRESS,
    /** Activity was completed successfully. */
    COMPLETED,
    /** Activity was cancelled before completion. */
    CANCELLED
}
