package tech.kayys.numou.identity.application.port;

import tech.kayys.numou.identity.application.dto.FederationSettings;
import tech.kayys.numou.identity.domain.idp.IdentityProviderConfig;

/**
 * Reads the federation settings an adapter needs out of
 * {@link IdentityProviderConfig#connectionConfigJson()}.
 *
 * <p>The application layer is JSON-free by design (ArchUnit forbids Jackson there),
 * so the interpretation of that opaque configuration blob lives behind this port and
 * is implemented by the adapter.
 */
public interface FederationSettingsPort {

    FederationSettings parse(IdentityProviderConfig config);
}
