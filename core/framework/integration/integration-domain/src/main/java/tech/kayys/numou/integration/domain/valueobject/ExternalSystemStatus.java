package tech.kayys.numou.integration.domain.valueobject;

import tech.kayys.numou.foundation.domain.valueobject.ValueObject;

/** Operational lifecycle of an external system registration. */
public enum ExternalSystemStatus implements ValueObject {

    /** Registered, still being validated against its sandbox. */
    PENDING,

    /** Verified and usable in production. */
    ACTIVE,

    /** Temporarily disabled (partner outage or incident). */
    SUSPENDED,

    /** Decommissioned; registrations are retained for audit. */
    RETIRED
}
