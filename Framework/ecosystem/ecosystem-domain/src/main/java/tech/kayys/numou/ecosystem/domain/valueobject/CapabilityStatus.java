package tech.kayys.numou.ecosystem.domain.valueobject;

import tech.kayys.numou.foundation.domain.valueobject.ValueObject;

/**
 * Lifecycle state of a capability definition in the catalog.
 */
public enum CapabilityStatus implements ValueObject {

    /** Proposed, not yet consumable by other participants. */
    DRAFT,

    /** Published and available for provider declaration / consumption. */
    ACTIVE,

    /** Still honoured, but no new consumers should adopt it. */
    DEPRECATED,

    /** Withdrawn from the catalog. */
    RETIRED
}
