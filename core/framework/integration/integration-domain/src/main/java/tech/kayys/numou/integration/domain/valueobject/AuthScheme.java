package tech.kayys.numou.integration.domain.valueobject;

import tech.kayys.numou.foundation.domain.valueobject.ValueObject;

/**
 * How Numou authenticates to an external system.
 *
 * <p>The enum never carries secrets - credentials live behind a
 * credential resolver port, referenced by handle.
 */
public enum AuthScheme implements ValueObject {

    /** Static API key / bearer token. */
    API_KEY,

    /** OAuth2 client-credentials grant. */
    OAUTH2_CLIENT_CREDENTIALS,

    /** Mutual TLS with a partner certificate. */
    MUTUAL_TLS,

    /** HTTP Basic, for legacy partners only. */
    BASIC,

    /** No authentication (sandbox / internal network only). */
    NONE
}
