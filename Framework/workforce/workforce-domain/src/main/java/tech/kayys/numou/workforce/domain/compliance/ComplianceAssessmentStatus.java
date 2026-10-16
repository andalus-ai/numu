package tech.kayys.numou.workforce.domain.compliance;

/**
 * Outcome status of a compliance assessment.
 */
public enum ComplianceAssessmentStatus {

    /** The worker fully satisfies the requirement. */
    COMPLIANT,

    /** The worker does not satisfy the requirement. */
    NON_COMPLIANT,

    /** The worker satisfies some but not all aspects of the requirement. */
    PARTIALLY_COMPLIANT,

    /** The requirement has been formally waived for this worker. */
    EXEMPT,

    /** The assessment has been initiated but not yet concluded. */
    PENDING
}
