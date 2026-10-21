package tech.kayys.numou.identity.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.identity.domain.idp.IdentityProviderConfig;
import tech.kayys.numou.identity.domain.user.FederatedIdentity;

/**
 * SPI for a SAML 2.0 identity provider: decodes a base64 {@code SAMLResponse}, verifies its
 * XML signature against the configured IdP certificate, validates conditions/audience/timing,
 * and extracts the subject plus attribute statements as a neutral {@link FederatedIdentity}.
 *
 * <p>All XML cryptography lives in the adapter; the application layer only sees verified claims.
 * Replay protection (one-time use of an assertion id) is the adapter's responsibility too.
 */
public interface SamlAssertionPort {

    /**
     * Validates a SAML 2.0 Response and extracts the asserted identity.
     *
     * @param config           identity provider configuration (entity id, ACS URL, IdP certificate)
     * @param samlResponse     raw base64 {@code SAMLResponse} value from the ACS POST
     * @param expectedInResponseTo request id this response must answer; {@code null} skips the check
     * @return the assertions the IdP made about the authenticated subject
     */
    Uni<FederatedIdentity> validateResponse(
            IdentityProviderConfig config, String samlResponse, String expectedInResponseTo);

    /**
     * Builds this service's SAML Service Provider metadata XML for one provider config,
     * published at {@code /api/v1/auth/saml/{providerId}/metadata} for the IdP admin to import.
     */
    String serviceProviderMetadata(IdentityProviderConfig config);
}
