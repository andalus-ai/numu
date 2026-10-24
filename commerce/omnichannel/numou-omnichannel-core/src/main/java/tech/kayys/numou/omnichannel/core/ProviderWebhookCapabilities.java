package tech.kayys.numou.omnichannel.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Declares security-relevant properties of a provider's documented webhook
 * protocol. This metadata is a readiness gate, not proof that an adapter is
 * correct: each provider adapter must be tested against official test vectors.
 */
public record ProviderWebhookCapabilities(
        String providerId,
        SignatureScheme signatureScheme,
        boolean signsExactRawBody,
        boolean requiresTimestampValidation,
        boolean requiresReplayProtection,
        boolean supportsSecretRotation,
        AccountIdentitySource accountIdentitySource,
        boolean officialProtocolReviewed) {

    public ProviderWebhookCapabilities {
        providerId = required(providerId, "providerId");
        Objects.requireNonNull(signatureScheme, "signatureScheme must not be null");
        Objects.requireNonNull(accountIdentitySource, "accountIdentitySource must not be null");
    }

    /**
     * True only when the generic M21 verifier is an appropriate complete
     * verifier for this declared protocol. Additional timestamp or replay
     * checks require a composed/provider-specific verifier instead.
     */
    public boolean canUseGenericRawBodyHmacVerifier() {
        return readinessIssuesForGenericVerifier().isEmpty();
    }

    /**
     * Returns stable, non-sensitive readiness codes. Never include secret,
     * signature, raw body, or request header values in these diagnostics.
     */
    public List<String> readinessIssuesForGenericVerifier() {
        List<String> issues = new ArrayList<>();
        if (!officialProtocolReviewed) {
            issues.add("OFFICIAL_PROTOCOL_NOT_REVIEWED");
        }
        if (signatureScheme != SignatureScheme.HMAC_SHA256_RAW_BODY_HEX) {
            issues.add("SIGNATURE_SCHEME_NOT_SUPPORTED_BY_GENERIC_VERIFIER");
        }
        if (!signsExactRawBody) {
            issues.add("PROTOCOL_DOES_NOT_SIGN_EXACT_RAW_BODY");
        }
        if (requiresTimestampValidation) {
            issues.add("TIMESTAMP_VALIDATION_REQUIRED");
        }
        if (requiresReplayProtection) {
            issues.add("REPLAY_PROTECTION_REQUIRED");
        }
        if (accountIdentitySource == AccountIdentitySource.UNKNOWN) {
            issues.add("ACCOUNT_IDENTITY_SOURCE_UNKNOWN");
        }
        return List.copyOf(issues);
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > 200) {
            throw new IllegalArgumentException(field + " must contain 1 to 200 characters");
        }
        return normalized;
    }

    public enum SignatureScheme {
        HMAC_SHA256_RAW_BODY_HEX,
        HMAC_SHA256_CANONICAL_STRING,
        HMAC_SHA256_RAW_BODY_BASE64,
        ASYMMETRIC_SIGNATURE,
        PROVIDER_SPECIFIC,
        NONE,
        UNKNOWN
    }

    public enum AccountIdentitySource {
        AUTHENTICATED_ROUTE,
        VERIFIED_PROVIDER_ACCOUNT_ID,
        TENANT_CONFIGURED_ENDPOINT,
        UNKNOWN
    }
}
