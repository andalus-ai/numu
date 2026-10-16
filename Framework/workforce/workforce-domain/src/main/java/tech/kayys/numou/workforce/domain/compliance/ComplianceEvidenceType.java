package tech.kayys.numou.workforce.domain.compliance;

/**
 * Classifies the form of a piece of compliance evidence.
 */
public enum ComplianceEvidenceType {

    /** A file or physical document uploaded as proof. */
    DOCUMENT,

    /** An official certificate issued by an authorised body. */
    CERTIFICATE,

    /** A signed or digital declaration by the worker themselves. */
    ATTESTATION,

    /** Evidence generated automatically by an internal system (e.g. training platform completion record). */
    SYSTEM_RECORD,

    /** Any evidence type not covered by the categories above. */
    OTHER
}
