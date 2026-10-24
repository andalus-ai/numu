package tech.kayys.numou.omnichannel.core;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Reusable verifier for providers whose documented protocol is HMAC-SHA-256
 * over the exact raw request body, with a hexadecimal signature header.
 *
 * This intentionally does not implement timestamped/canonical-string
 * protocols. Use a provider-specific verifier for those protocols. The caller
 * must authenticate provider-account routing before invoking this verifier.
 */
public final class HmacSha256RawBodyWebhookVerifier implements ProviderWebhookVerifier {
    private static final String ALGORITHM = "HmacSHA256";

    private final String providerId;
    private final String signatureHeader;
    private final String signaturePrefix;
    private final ProviderWebhookSecretResolver secretResolver;

    public HmacSha256RawBodyWebhookVerifier(
            String providerId,
            String signatureHeader,
            String signaturePrefix,
            ProviderWebhookSecretResolver secretResolver) {
        this.providerId = required(providerId, "providerId");
        this.signatureHeader = required(signatureHeader, "signatureHeader");
        this.signaturePrefix = Objects.requireNonNull(signaturePrefix, "signaturePrefix must not be null");
        if (signaturePrefix.length() > 32) {
            throw new IllegalArgumentException("signaturePrefix must not exceed 32 characters");
        }
        this.secretResolver = Objects.requireNonNull(secretResolver, "secretResolver must not be null");
    }

    @Override
    public String providerId() {
        return providerId;
    }

    @Override
    public WebhookVerificationResult verify(ProviderWebhookVerificationRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        if (!providerId.equals(request.providerId())) {
            return WebhookVerificationResult.rejected("PROVIDER_MISMATCH");
        }

        Optional<String> header = signatureHeader(request.headers());
        if (header.isEmpty()) {
            return WebhookVerificationResult.rejected("MISSING_SIGNATURE");
        }

        String supplied = header.get().trim();
        if (!signaturePrefix.isEmpty()) {
            if (!supplied.startsWith(signaturePrefix)) {
                return WebhookVerificationResult.rejected("MALFORMED_SIGNATURE");
            }
            supplied = supplied.substring(signaturePrefix.length());
        }

        final byte[] suppliedBytes;
        try {
            suppliedBytes = HexFormat.of().parseHex(supplied);
        } catch (IllegalArgumentException exception) {
            return WebhookVerificationResult.rejected("MALFORMED_SIGNATURE");
        }
        if (suppliedBytes.length != 32) {
            return WebhookVerificationResult.rejected("MALFORMED_SIGNATURE");
        }

        final Optional<byte[]> resolved;
        try {
            resolved = secretResolver.resolve(request.providerId(), request.providerAccountId());
        } catch (RuntimeException exception) {
            return WebhookVerificationResult.rejected("SECRET_UNAVAILABLE");
        }
        if (resolved == null || resolved.isEmpty() || resolved.get() == null || resolved.get().length == 0) {
            return WebhookVerificationResult.rejected("SECRET_UNAVAILABLE");
        }

        byte[] secret = resolved.get().clone();
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            byte[] expected = mac.doFinal(request.rawBody());
            return MessageDigest.isEqual(expected, suppliedBytes)
                    ? WebhookVerificationResult.success()
                    : WebhookVerificationResult.rejected("INVALID_SIGNATURE");
        } catch (GeneralSecurityException exception) {
            return WebhookVerificationResult.rejected("VERIFICATION_UNAVAILABLE");
        } finally {
            java.util.Arrays.fill(secret, (byte) 0);
        }
    }

    private Optional<String> signatureHeader(Map<String, String> headers) {
        String value = null;
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (!signatureHeader.equalsIgnoreCase(entry.getKey())) {
                continue;
            }
            if (value != null) {
                return Optional.empty();
            }
            value = entry.getValue();
        }
        return Optional.ofNullable(value).filter(candidate -> !candidate.isBlank());
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > 200) {
            throw new IllegalArgumentException(field + " must contain 1 to 200 characters");
        }
        return normalized;
    }
}
