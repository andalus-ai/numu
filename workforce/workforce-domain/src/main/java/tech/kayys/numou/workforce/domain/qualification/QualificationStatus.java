package tech.kayys.numou.workforce.domain.qualification;

/**
 * Lifecycle state of a {@link Qualification} catalogue entry.
 */
public enum QualificationStatus {

    /** The qualification is active and can be assigned to workers. */
    ACTIVE,

    /** The qualification has been deactivated (no new assignments allowed). */
    INACTIVE
}
