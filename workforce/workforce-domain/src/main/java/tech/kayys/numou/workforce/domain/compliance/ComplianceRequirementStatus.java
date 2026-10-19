package tech.kayys.numou.workforce.domain.compliance;

/**
 * Lifecycle status of a compliance requirement.
 */
public enum ComplianceRequirementStatus {

    /** The requirement is currently in force and must be met. */
    ACTIVE,

    /** The requirement has been administratively deactivated and is no longer enforced. */
    INACTIVE,

    /** The requirement has been replaced by a newer version or a different requirement. */
    SUPERSEDED
}
