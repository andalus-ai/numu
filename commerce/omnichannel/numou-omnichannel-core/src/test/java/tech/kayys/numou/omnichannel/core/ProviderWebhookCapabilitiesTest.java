package tech.kayys.numou.omnichannel.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProviderWebhookCapabilitiesTest {

    @Test
    void permitsGenericVerifierOnlyForReviewedCompatibleProtocol() {
        var capabilities = new ProviderWebhookCapabilities(
                "example-provider",
                ProviderWebhookCapabilities.SignatureScheme.HMAC_SHA256_RAW_BODY_HEX,
                true,
                false,
                false,
                true,
                ProviderWebhookCapabilities.AccountIdentitySource.AUTHENTICATED_ROUTE,
                true);

        assertThat(capabilities.canUseGenericRawBodyHmacVerifier()).isTrue();
        assertThat(capabilities.readinessIssuesForGenericVerifier()).isEmpty();
    }

    @Test
    void blocksProtocolsRequiringTimestampOrReplayValidation() {
        var capabilities = new ProviderWebhookCapabilities(
                "example-provider",
                ProviderWebhookCapabilities.SignatureScheme.HMAC_SHA256_RAW_BODY_HEX,
                true,
                true,
                true,
                true,
                ProviderWebhookCapabilities.AccountIdentitySource.AUTHENTICATED_ROUTE,
                true);

        assertThat(capabilities.canUseGenericRawBodyHmacVerifier()).isFalse();
        assertThat(capabilities.readinessIssuesForGenericVerifier())
                .contains("TIMESTAMP_VALIDATION_REQUIRED", "REPLAY_PROTECTION_REQUIRED");
    }

    @Test
    void blocksCanonicalSchemesUnreviewedProtocolsAndUnknownAccountRouting() {
        var capabilities = new ProviderWebhookCapabilities(
                "example-provider",
                ProviderWebhookCapabilities.SignatureScheme.HMAC_SHA256_CANONICAL_STRING,
                false,
                false,
                false,
                false,
                ProviderWebhookCapabilities.AccountIdentitySource.UNKNOWN,
                false);

        assertThat(capabilities.canUseGenericRawBodyHmacVerifier()).isFalse();
        assertThat(capabilities.readinessIssuesForGenericVerifier())
                .contains(
                        "OFFICIAL_PROTOCOL_NOT_REVIEWED",
                        "SIGNATURE_SCHEME_NOT_SUPPORTED_BY_GENERIC_VERIFIER",
                        "PROTOCOL_DOES_NOT_SIGN_EXACT_RAW_BODY",
                        "ACCOUNT_IDENTITY_SOURCE_UNKNOWN");
    }

    @Test
    void rejectsMissingRequiredMetadata() {
        assertThatThrownBy(() -> new ProviderWebhookCapabilities(
                " ",
                ProviderWebhookCapabilities.SignatureScheme.UNKNOWN,
                false,
                false,
                false,
                false,
                ProviderWebhookCapabilities.AccountIdentitySource.UNKNOWN,
                false))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
