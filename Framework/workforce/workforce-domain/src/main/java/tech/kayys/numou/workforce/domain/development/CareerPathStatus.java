package tech.kayys.numou.workforce.domain.development;

/**
 * Lifecycle states of a {@link CareerPath}.
 */
public enum CareerPathStatus {
    /** Career path is published and available for assignment to workers. */
    ACTIVE,
    /** Career path is not available for new assignments. */
    INACTIVE
}
