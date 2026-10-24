package tech.kayys.numou.omnichannel.core;

/**
 * Provider-specific authenticity verification port. Implementations must use
 * the provider's documented signing algorithm, timestamp/replay policy, and
 * constant-time signature comparison where applicable.
 */
public interface ProviderWebhookVerifier {

    /** Stable provider identifier this verifier supports. */
    String providerId();

    /** Returns a safe result code; never return or log secret/signature material. */
    WebhookVerificationResult verify(ProviderWebhookVerificationRequest request);
}
