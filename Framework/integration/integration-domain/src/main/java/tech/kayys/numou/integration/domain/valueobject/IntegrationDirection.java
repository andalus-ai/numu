package tech.kayys.syirkah.integration.domain.valueobject;

import tech.kayys.syirkah.foundation.domain.valueobject.ValueObject;

/** Which way data flows between Numou and an external system. */
public enum IntegrationDirection implements ValueObject {

    /** Partner pushes into Numou. */
    INBOUND,

    /** Numou pushes out to the partner. */
    OUTBOUND,

    /** Both, typically via separate API and webhook channels. */
    BIDIRECTIONAL
}
