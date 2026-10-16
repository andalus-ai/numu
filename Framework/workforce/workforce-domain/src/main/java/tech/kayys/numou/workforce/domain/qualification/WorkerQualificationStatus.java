package tech.kayys.numou.workforce.domain.qualification;

/**
 * Lifecycle state of a {@link WorkerQualification} record.
 */
public enum WorkerQualificationStatus {

    /** The qualification is currently valid for this worker. */
    ACTIVE,

    /** The qualification has been revoked (e.g., expired, withdrawn). */
    REVOKED
}
