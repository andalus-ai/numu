package tech.kayys.numou.identity.application.dto;

import java.util.Objects;

/**
 * Data the browser carries back from the provider callback (via an HttpOnly cookie)
 * so the application layer can complete the PKCE flow it started.
 *
 * @param code         authorization code returned by the provider
 * @param state        state returned by the provider (must equal the one issued at authorize time)
 * @param codeVerifier PKCE verifier for {@code codeChallenge}
 * @param nonce        nonce that must appear in the ID token
 */
public record FederationCallback(String code, String state, String codeVerifier, String nonce) {

    public FederationCallback {
        Objects.requireNonNull(code, "code cannot be null");
        Objects.requireNonNull(state, "state cannot be null");
        Objects.requireNonNull(codeVerifier, "codeVerifier cannot be null");
        Objects.requireNonNull(nonce, "nonce cannot be null");
        if (code.isBlank() || state.isBlank() || codeVerifier.isBlank() || nonce.isBlank()) {
            throw new IllegalArgumentException("code, state, codeVerifier and nonce must be non-blank");
        }
    }
}
