package tech.kayys.numou.omnichannel.core;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Reusable behavioral checks for provider webhook verifier implementations.
 *
 * Provider-specific test classes should invoke these checks using official
 * protocol test vectors and their own malformed/timestamp/replay cases. These
 * generic checks deliberately do not invent a provider's signing format.
 */
abstract class ProviderWebhookVerifierContractTest {

    protected abstract ProviderWebhookVerifier verifier();

    protected abstract String expectedProviderId();

    protected abstract ProviderWebhookVerificationRequest validRequest();

    protected abstract ProviderWebhookVerificationRequest invalidSignatureRequest();

    @Test
    void verifierHasStableProviderIdentity() {
        assertThat(verifier().providerId()).isEqualTo(expectedProviderId());
        assertThat(verifier().providerId()).isNotBlank();
    }

    @Test
    void acceptsOfficialValidTestVector() {
        WebhookVerificationResult result = verifier().verify(validRequest());

        assertThat(result).isNotNull();
        assertThat(result.verified()).isTrue();
        assertThat(result.reasonCode()).isEqualTo("VERIFIED");
    }

    @Test
    void rejectsInvalidSignatureWithoutExposingSensitiveMaterial() {
        ProviderWebhookVerificationRequest request = invalidSignatureRequest();
        WebhookVerificationResult result = verifier().verify(request);

        assertThat(result).isNotNull();
        assertThat(result.verified()).isFalse();
        assertThat(result.reasonCode()).isNotBlank();
        assertThat(result.reasonCode()).doesNotContain(
                new String(request.rawBody(), StandardCharsets.UTF_8));
        request.headers().values().stream()
                .filter(value -> value != null && !value.isBlank())
                .forEach(value -> assertThat(result.reasonCode()).doesNotContain(value));
    }

    @Test
    void rejectsRequestForAnotherProvider() {
        ProviderWebhookVerificationRequest valid = validRequest();
        String otherProvider = expectedProviderId() + "-unexpected";
        ProviderWebhookVerificationRequest wrongProvider = new ProviderWebhookVerificationRequest(
                otherProvider, valid.providerAccountId(), valid.headers(), valid.rawBody());

        WebhookVerificationResult result = verifier().verify(wrongProvider);

        assertThat(result).isNotNull();
        assertThat(result.verified()).isFalse();
    }

    @Test
    void verificationResultContractCannotRepresentContradictoryStates() {
        assertThatThrownBy(() -> new WebhookVerificationResult(true, "INVALID_SIGNATURE"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WebhookVerificationResult(false, "VERIFIED"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
