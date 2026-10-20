package tech.kayys.numou.identity.application.dto;

import java.util.Objects;

/**
 * Per-login randomness handed to the provider to start an authorization flow.
 *
 * @param state         CSRF token the callback must echo back
 * @param nonce         replay guard verified inside the ID token
 * @param codeChallenge PKCE S256 challenge of {@code codeVerifier}
 */
public record FederationAuthorizationRequest(String state, String nonce, String codeChallenge) {

    public FederationAuthorizationRequest {
        Objects.requireNonNull(state, "state cannot be null");
        Objects.requireNonNull(nonce, "nonce cannot be null");
        Objects.requireNonNull(codeChallenge, "codeChallenge cannot be null");
        if (state.isBlank() || nonce.isBlank() || codeChallenge.isBlank()) {
            throw new IllegalArgumentException("state, nonce and codeChallenge must be non-blank");
        }
    }
}
