package tech.kayys.syirkah.integration.spi.port;

import io.smallrye.mutiny.Uni;

import java.util.Optional;

/**
 * Resolves a credential handle to a usable secret at call time.
 *
 * <p>The domain only ever stores a handle. Secrets live in a vault (or a
 * test double) and are fetched just before use, so rotating them never
 * requires touching domain code.
 */
public interface CredentialResolverPort {

    Uni<Optional<String>> resolve(String credentialHandle);
}
