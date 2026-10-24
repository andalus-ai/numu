package tech.kayys.numou.omnichannel.core;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class HmacSha256RawBodyWebhookVerifierTest {
    private static final String PROVIDER = "test-provider";
    private static final String ACCOUNT = "account-42";
    private static final byte[] SECRET = "test-secret-not-for-production".getBytes(StandardCharsets.UTF_8);
    private static final byte[] BODY = "{\"type\":\"order.updated\",\"id\":\"evt-1\"}"
            .getBytes(StandardCharsets.UTF_8);

    @Test
    void verifiesHmacOverExactRawBytesAndUsesCaseInsensitiveHeaderName() throws Exception {
        AtomicReference<String> resolvedProvider = new AtomicReference<>();
        AtomicReference<String> resolvedAccount = new AtomicReference<>();
        var verifier = verifier((provider, account) -> {
            resolvedProvider.set(provider);
            resolvedAccount.set(account);
            return Optional.of(SECRET.clone());
        });

        var result = verifier.verify(request("X-PROVIDER-SIGNATURE", "sha256=" + signature(BODY), BODY));

        assertThat(result).isEqualTo(WebhookVerificationResult.success());
        assertThat(resolvedProvider).hasValue(PROVIDER);
        assertThat(resolvedAccount).hasValue(ACCOUNT);
    }

    @Test
    void rejectsModifiedBodyEvenWhenOriginalSignatureIsReused() throws Exception {
        byte[] changedBody = "{\"type\":\"order.deleted\",\"id\":\"evt-1\"}"
                .getBytes(StandardCharsets.UTF_8);
        var result = verifier((provider, account) -> Optional.of(SECRET.clone()))
                .verify(request("x-provider-signature", "sha256=" + signature(BODY), changedBody));

        assertThat(result).isEqualTo(WebhookVerificationResult.rejected("INVALID_SIGNATURE"));
    }

    @Test
    void rejectsMissingMalformedAndAmbiguousSignatureHeaders() throws Exception {
        var verifier = verifier((provider, account) -> Optional.of(SECRET.clone()));
        assertThat(verifier.verify(new ProviderWebhookVerificationRequest(
                PROVIDER, ACCOUNT, Map.of(), BODY)))
                .isEqualTo(WebhookVerificationResult.rejected("MISSING_SIGNATURE"));
        assertThat(verifier.verify(request("x-provider-signature", "sha256=not-hex", BODY)))
                .isEqualTo(WebhookVerificationResult.rejected("MALFORMED_SIGNATURE"));
        assertThat(verifier.verify(new ProviderWebhookVerificationRequest(PROVIDER, ACCOUNT,
                Map.of("x-provider-signature", "sha256=" + signature(BODY),
                       "X-Provider-Signature", "sha256=" + signature(BODY)), BODY)))
                .isEqualTo(WebhookVerificationResult.rejected("MISSING_SIGNATURE"));
    }

    @Test
    void rejectsUnavailableSecretAndProviderMismatch() throws Exception {
        var noSecret = verifier((provider, account) -> Optional.empty());
        assertThat(noSecret.verify(request("x-provider-signature", "sha256=" + signature(BODY), BODY)))
                .isEqualTo(WebhookVerificationResult.rejected("SECRET_UNAVAILABLE"));

        var wrongProvider = new ProviderWebhookVerificationRequest(
                "another-provider", ACCOUNT, Map.of("x-provider-signature", "sha256=" + signature(BODY)), BODY);
        assertThat(verifier((provider, account) -> Optional.of(SECRET.clone())).verify(wrongProvider))
                .isEqualTo(WebhookVerificationResult.rejected("PROVIDER_MISMATCH"));
    }

    @Test
    void neverTreatsASecretResolverExceptionAsAnAuthenticationSuccess() throws Exception {
        var verifier = verifier((provider, account) -> {
            throw new IllegalStateException("secret store details must not escape");
        });
        assertThat(verifier.verify(request("x-provider-signature", "sha256=" + signature(BODY), BODY)))
                .isEqualTo(WebhookVerificationResult.rejected("SECRET_UNAVAILABLE"));
    }

    private static HmacSha256RawBodyWebhookVerifier verifier(ProviderWebhookSecretResolver resolver) {
        return new HmacSha256RawBodyWebhookVerifier(
                PROVIDER, "x-provider-signature", "sha256=", resolver);
    }

    private static ProviderWebhookVerificationRequest request(String header, String signature, byte[] body) {
        return new ProviderWebhookVerificationRequest(
                PROVIDER, ACCOUNT, Map.of(header, signature), body);
    }

    private static String signature(byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET, "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body));
    }
}
