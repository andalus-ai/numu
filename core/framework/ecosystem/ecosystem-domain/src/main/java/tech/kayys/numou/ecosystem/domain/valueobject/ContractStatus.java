package tech.kayys.numou.ecosystem.domain.valueobject;

import tech.kayys.numou.foundation.domain.valueobject.ValueObject;

/**
 * Lifecycle state of an ecosystem contract.
 */
public enum ContractStatus implements ValueObject {

    /** Being negotiated. */
    DRAFT,

    /** Signed and currently governing the relationship. */
    ACTIVE,

    /** Past its end date, no renewal recorded. */
    EXPIRED,

    /** Ended early by either party. */
    TERMINATED
}
