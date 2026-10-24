package tech.kayys.numou.omnichannel.core;

import java.util.Optional;

/**
 * Resolves a provider signing key for a previously authenticated provider
 * account. Implementations must scope lookup by both arguments, never log or
 * return a secret through diagnostics, and should retrieve secrets from a
 * managed secret store rather than source code or ordinary application logs.
 */
@FunctionalInterface
public interface ProviderWebhookSecretResolver {

    /** Returns signing-key bytes for the exact provider/account pair, if configured. */
    Optional<byte[]> resolve(String providerId, String providerAccountId);
}
