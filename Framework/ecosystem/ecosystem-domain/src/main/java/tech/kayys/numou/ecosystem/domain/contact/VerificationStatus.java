package tech.kayys.syirkah.ecosystem.domain.contact;

/**
 * Verification state of an endpoint, distinct from user consent (config03.md §P4-13).
 */
public enum VerificationStatus {
    UNVERIFIED,
    PENDING,
    VERIFIED,
    FAILED
}
