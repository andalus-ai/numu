package tech.kayys.syirkah.project.domain.commercial;

/**
 * Change request proposal lifecycle.
 *
 * A change request is a proposal; only once approved does it become
 * (or justify) a change order, which is the contractual instrument.
 */
public enum ChangeRequestStatus {

    REQUESTED,

    UNDER_REVIEW,

    ASSESSED,

    APPROVED,

    REJECTED,

    WITHDRAWN
}