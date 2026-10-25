package tech.kayys.numou.omnichannel.core;

import java.util.Optional;

/**
 * Provider-specific extraction of the protocol's replay identifier. Extract
 * only a value covered by the verified signature; never return raw body data
 * or a secret. The value is hashed before it reaches the replay store.
 */
@FunctionalInterface
public interface ProviderWebhookReplayKeyExtractor {
    Optional<String> extract(ProviderWebhookVerificationRequest request);
}