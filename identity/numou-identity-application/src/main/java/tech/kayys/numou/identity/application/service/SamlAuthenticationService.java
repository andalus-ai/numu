package tech.kayys.numou.identity.application.service;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.identity.application.dto.FederatedLoginResponse;
import tech.kayys.numou.identity.application.port.SamlAssertionPort;
import tech.kayys.numou.identity.domain.idp.IdentityProviderType;

import java.util.Objects;
import java.util.UUID;

/**
 * SAML 2.0 Service Provider login: validates the assertion the IdP POSTed to the ACS,
 * then runs the exact same JIT provisioning and group-to-role synchronization as OIDC.
 *
 * <p>Request-id correlation ({@code InResponseTo}) is the resource's job: the browser flow
 * keeps the issued request id in the flow cookie, and the unsolicited (IdP-initiated) variant
 * simply carries none.
 */
public class SamlAuthenticationService {

    private final FederatedAuthenticationService federation;
    private final SamlAssertionPort saml;

    public SamlAuthenticationService(
            FederatedAuthenticationService federation,
            SamlAssertionPort saml
    ) {
        this.federation = Objects.requireNonNull(federation);
        this.saml = Objects.requireNonNull(saml);
    }

    /**
     * Consumes one base64 {@code SAMLResponse} from the ACS and returns an internal access token.
     *
     * @param providerId            SAML provider config id
     * @param samlResponse          raw base64 {@code SAMLResponse} POST field
     * @param expectedInResponseTo  request id this response must answer; {@code null} for
     *                              unsolicited (IdP-initiated) logins
     */
    public Uni<FederatedLoginResponse> consume(UUID providerId, String samlResponse, String expectedInResponseTo) {
        Objects.requireNonNull(providerId, "providerId cannot be null");
        if (samlResponse == null || samlResponse.isBlank()) {
            return Uni.createFrom().failure(ApplicationError.of(
                    "SAML_RESPONSE_REQUIRED", "SAMLResponse is required").toException());
        }

        return federation.requireProvider(providerId, IdentityProviderType.SAML, "SAML 2.0")
                .chain(config -> saml.validateResponse(config, samlResponse, expectedInResponseTo)
                        .chain(identity -> federation.authenticate(config, identity)));
    }
}
