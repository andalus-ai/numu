package tech.kayys.numou.omnichannel.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProviderWebhookAdapterRegistryTest {
    private static final ProviderWebhookCapabilities.AccountIdentitySource ROUTE =
            ProviderWebhookCapabilities.AccountIdentitySource.AUTHENTICATED_ROUTE;

    @Test
    void registersReviewedCompatibleGenericVerifierAndExposesImmutableSnapshot() {
        var capabilities = genericCapabilities("shop-provider");
        var verifier = genericVerifier("shop-provider");
        var registry = new ProviderWebhookAdapterRegistry(List.of(
                new ProviderWebhookAdapterRegistration(capabilities, verifier)));

        assertThat(registry.supports("shop-provider")).isTrue();
        assertThat(registry.verifierFor("shop-provider")).isSameAs(verifier);
        assertThat(registry.capabilitiesFor("shop-provider")).isEqualTo(capabilities);
        assertThat(registry.verifiers()).containsExactly(verifier);
        assertThat(registry.size()).isEqualTo(1);
    }

    @Test
    void rejectsUnreviewedProtocolAndUnknownAccountIdentity() {
        var unreviewed = new ProviderWebhookCapabilities(
                "shop-provider",
                ProviderWebhookCapabilities.SignatureScheme.HMAC_SHA256_RAW_BODY_HEX,
                true, false, false, true, ROUTE, false);
        assertThatThrownBy(() -> new ProviderWebhookAdapterRegistration(
                unreviewed, genericVerifier("shop-provider")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("protocol must be reviewed");

        var unknownAccount = new ProviderWebhookCapabilities(
                "shop-provider",
                ProviderWebhookCapabilities.SignatureScheme.HMAC_SHA256_RAW_BODY_HEX,
                true, false, false, true,
                ProviderWebhookCapabilities.AccountIdentitySource.UNKNOWN, true);
        assertThatThrownBy(() -> new ProviderWebhookAdapterRegistration(
                unknownAccount, genericVerifier("shop-provider")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("identity source");
    }

    @Test
    void rejectsGenericVerifierWhenCapabilitiesRequireTimestampOrReplayValidation() {
        var incompatible = new ProviderWebhookCapabilities(
                "shop-provider",
                ProviderWebhookCapabilities.SignatureScheme.HMAC_SHA256_RAW_BODY_HEX,
                true, true, true, true, ROUTE, true);

        assertThatThrownBy(() -> new ProviderWebhookAdapterRegistration(
                incompatible, genericVerifier("shop-provider")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("incompatible");
    }

    @Test
    void rejectsProviderSpecificVerifierWithoutRequiredTimestampAndReplayControls() {
        var requiredControls = providerSpecificCapabilities("timestamped-provider");
        assertThatThrownBy(() -> new ProviderWebhookAdapterRegistration(
                requiredControls, providerSpecificVerifier("timestamped-provider", false, false)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires timestamp validation");
    }

    @Test
    void acceptsProviderSpecificVerifierOnlyWhenItDeclaresRequiredControls() {
        var requiredControls = providerSpecificCapabilities("timestamped-provider");
        var verifier = providerSpecificVerifier("timestamped-provider", true, true);

        var registration = new ProviderWebhookAdapterRegistration(requiredControls, verifier);

        assertThat(registration.verifier().validatesProviderTimestamp()).isTrue();
        assertThat(registration.verifier().enforcesReplayProtection()).isTrue();
    }

    @Test
    void rejectsProviderIdentityMismatchAndDuplicateProviderIds() {
        assertThatThrownBy(() -> new ProviderWebhookAdapterRegistration(
                genericCapabilities("shop-provider"), genericVerifier("other-provider")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must match");

        var first = new ProviderWebhookAdapterRegistration(
                genericCapabilities("shop-provider"), genericVerifier("shop-provider"));
        var second = new ProviderWebhookAdapterRegistration(
                genericCapabilities("shop-provider"), genericVerifier("shop-provider"));
        assertThatThrownBy(() -> new ProviderWebhookAdapterRegistry(List.of(first, second)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void unknownProviderIsNotSupported() {
        var registry = new ProviderWebhookAdapterRegistry(List.of());
        assertThat(registry.supports("missing-provider")).isFalse();
        assertThat(registry.verifierFor("missing-provider")).isNull();
        assertThat(registry.capabilitiesFor("missing-provider")).isNull();
    }

    private static ProviderWebhookCapabilities genericCapabilities(String providerId) {
        return new ProviderWebhookCapabilities(
                providerId,
                ProviderWebhookCapabilities.SignatureScheme.HMAC_SHA256_RAW_BODY_HEX,
                true, false, false, true, ROUTE, true);
    }

    private static ProviderWebhookCapabilities providerSpecificCapabilities(String providerId) {
        return new ProviderWebhookCapabilities(
                providerId,
                ProviderWebhookCapabilities.SignatureScheme.PROVIDER_SPECIFIC,
                false, true, true, true, ROUTE, true);
    }

    private static ProviderWebhookVerifier genericVerifier(String providerId) {
        return new HmacSha256RawBodyWebhookVerifier(
                providerId, "x-provider-signature", "sha256=",
                (provider, account) -> Optional.of("test-secret".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    private static ProviderWebhookVerifier providerSpecificVerifier(
            String providerId, boolean timestampValidation, boolean replayProtection) {
        return new ProviderWebhookVerifier() {
            @Override
            public String providerId() {
                return providerId;
            }

            @Override
            public WebhookVerificationResult verify(ProviderWebhookVerificationRequest request) {
                return WebhookVerificationResult.success();
            }

            @Override
            public boolean validatesProviderTimestamp() {
                return timestampValidation;
            }

            @Override
            public boolean enforcesReplayProtection() {
                return replayProtection;
            }
        };
    }
}
