package tech.kayys.syirkah.security.spi.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.security.domain.authentication.AuthenticationResult;
import tech.kayys.syirkah.security.domain.authorization.Principal;

import java.util.Optional;

/**
 * "Who are you?" (base01.md §P1-17, security02.md §P3-18).
 *
 * <p>Implemented by password verification, OIDC token validation, mTLS or
 * an in-memory test double. The domain never sees raw credentials - it only
 * receives an authenticated {@link AuthenticationResult} with canonical {@link Principal}.
 */
public interface AuthenticationPort {

    /**
     * Authenticates an incoming authentication request.
     *
     * @param request the authentication request
     * @return reactive Uni containing the authentication result
     */
    Uni<AuthenticationResult> authenticate(AuthenticationRequest request);

    /**
     * Convenience legacy overload.
     */
    default Uni<Optional<Principal>> authenticate(String credential) {
        if (credential == null || credential.isBlank()) {
            return Uni.createFrom().item(Optional.<Principal>empty());
        }
        return authenticate(new AuthenticationRequest(CredentialType.BEARER_TOKEN, credential))
                .map(result -> Optional.ofNullable(result.principal()))
                .onFailure().recoverWithItem(Optional.<Principal>empty());
    }
}
