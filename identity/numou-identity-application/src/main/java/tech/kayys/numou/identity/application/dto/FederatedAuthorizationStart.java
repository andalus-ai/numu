package tech.kayys.numou.identity.application.dto;

import java.util.Objects;

/**
 * Everything the frontend needs to complete a federated login:
 * the authorization URL to redirect to and the PKCE/CSRF material
 * that must survive until the callback arrives.
 *
 * @param state            CSRF token to persist in the browser
 * @param nonce            replay guard to persist in the browser
 * @param codeVerifier     PKCE secret to persist in the browser (never leaves it unencrypted in transit)
 * @param authorizationUrl provider URL the browser must be redirected to
 */
public record FederatedAuthorizationStart(
        String state,
        String nonce,
        String codeVerifier,
        String authorizationUrl
) {

    public FederatedAuthorizationStart {
        Objects.requireNonNull(state, "state cannot be null");
        Objects.requireNonNull(nonce, "nonce cannot be null");
        Objects.requireNonNull(codeVerifier, "codeVerifier cannot be null");
        Objects.requireNonNull(authorizationUrl, "authorizationUrl cannot be null");
    }
}
