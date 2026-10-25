package tech.kayys.numou.omnichannel.core;

/**
 * Provider-specific authenticity verification port. Implementations must use
 * the provider's documented signing algorithm, timestamp/replay policy, and
 * constant-time signature comparison where applicable.
 *
 * <p>Security-control declarations are registration gates, not proof of
 * correctness. Implementations must only return {@code true} after enforcing
 * the provider's documented policy and passing provider-specific test vectors.
 * The defaults fail closed for protocols requiring timestamp or replay checks.</p>
 */
public interface ProviderWebhookVerifier {

    /** Stable provider identifier this verifier supports. */
    String providerId();

    /** Returns a safe result code; never return or log secret/signature material. */
    WebhookVerificationResult verify(ProviderWebhookVerificationRequest request);

    /**
     * Whether this verifier validates the provider-signed timestamp and rejects
     * timestamps outside the provider's configured acceptance window.
     */
    default boolean validatesProviderTimestamp() {
        return false;
    }

    /**
     * Whether this verifier enforces the provider's replay policy, including
     * atomic durable replay-key reservation when required by that protocol.
     */
    default boolean enforcesReplayProtection() {
        return false;
    }
}
