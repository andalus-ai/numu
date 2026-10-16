package tech.kayys.syirkah.ecosystem.domain.valueobject;

import tech.kayys.syirkah.foundation.domain.valueobject.ValueObject;

/**
 * Lifecycle state of a participant.
 */
public enum ParticipantStatus implements ValueObject {

    /** Registered but not yet verified / onboarded. */
    PENDING,

    /** Verified and allowed to transact in the ecosystem. */
    ACTIVE,

    /** Temporarily blocked, historical obligations remain. */
    SUSPENDED,

    /** No longer part of the ecosystem; kept for audit and history. */
    RETIRED
}
