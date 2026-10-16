package tech.kayys.numou.workforce.domain.compliance;

/**
 * Classifies the nature of a compliance requirement.
 */
public enum ComplianceRequirementType {

    /** Mandated by external law, regulation, or government authority. */
    REGULATORY,

    /** Internal organisational policy that employees must follow. */
    POLICY,

    /** A professional or vocational certification that must be obtained/maintained. */
    CERTIFICATION,

    /** A training programme or course that must be completed. */
    TRAINING,

    /** Submission or maintenance of specific documentation (e.g. work permits, contracts). */
    DOCUMENTATION,

    /** Any requirement not covered by the categories above. */
    OTHER
}
