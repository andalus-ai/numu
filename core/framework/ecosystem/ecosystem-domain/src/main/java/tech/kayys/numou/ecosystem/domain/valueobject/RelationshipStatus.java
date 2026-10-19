package tech.kayys.numou.ecosystem.domain.valueobject;

import tech.kayys.numou.foundation.domain.valueobject.ValueObject;

/**
 * Lifecycle state of a participant relationship.
 */
public enum RelationshipStatus implements ValueObject {

    /** Agreed in principle, not yet effective. */
    PROPOSED,

    /** Effective: transactions may reference this relationship. */
    ACTIVE,

    /** Temporarily not usable, history preserved. */
    SUSPENDED,

    /** Ended. Kept for audit, never deleted. */
    TERMINATED
}
