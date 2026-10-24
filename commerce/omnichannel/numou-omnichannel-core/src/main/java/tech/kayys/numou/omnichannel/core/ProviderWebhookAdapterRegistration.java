package tech.kayys.numou.omnichannel.core;

import java.util.Objects;

/**
 * Binds a provider's reviewed protocol declaration to the verifier that
 * implements it. The declaration is a release gate, not a substitute for
 * implementation tests against official provider test vectors.
 */
public record ProviderWebhookAdapterRegistration(
        ProviderWebhookCapabilities capabilities,
        ProviderWebhookVerifier verifier) {

    public ProviderWebhookAdapterRegistration {
        Objects.requireNonNull(capabilities, "capabilities must not be null");
        Objects.requireNonNull(verifier, "verifier must not be null");

        if (!capabilities.providerId().equals(verifier.providerId())) {
            throw new IllegalArgumentException("capability providerId must match verifier providerId");
        }
        if (!capabilities.officialProtocolReviewed()) {
            throw new IllegalArgumentException("provider protocol must be reviewed before registration");
        }
        if (capabilities.accountIdentitySource() == ProviderWebhookCapabilities.AccountIdentitySource.UNKNOWN) {
            throw new IllegalArgumentException("provider account identity source must be known before registration");
        }
        if (verifier instanceof HmacSha256RawBodyWebhookVerifier
                && !capabilities.canUseGenericRawBodyHmacVerifier()) {
            throw new IllegalArgumentException(
                    "generic raw-body HMAC verifier is incompatible with declared provider capabilities");
        }
    }
}
