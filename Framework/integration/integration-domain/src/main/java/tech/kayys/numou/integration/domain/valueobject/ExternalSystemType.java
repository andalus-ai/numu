package tech.kayys.syirkah.integration.domain.valueobject;

import tech.kayys.syirkah.foundation.domain.valueobject.ValueObject;

/**
 * What kind of partner system this is. Deliberately coarse: the type drives
 * discovery and operational policy, never business logic.
 */
public enum ExternalSystemType implements ValueObject {

    /** Partner's own fleet management system (they keep it, we integrate). */
    EXTERNAL_FMS,

    /** Partner's own ERP / accounting system. */
    EXTERNAL_ERP,

    /** Third-party logistics provider platform. */
    EXTERNAL_3PL,

    /** Payment service provider / gateway. */
    PAYMENT_GATEWAY,

    /** Tax authority or e-invoicing clearing house. */
    TAX_AUTHORITY,

    /** Marketplace or e-commerce channel. */
    MARKETPLACE,

    /** Anything else, described by a named connector. */
    CUSTOM
}
