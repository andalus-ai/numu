package tech.kayys.numou.omnichannel.core;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Provider verification outcome with a non-sensitive machine-readable reason.
 * Reason codes are deliberately constrained to stable tokens so implementations
 * cannot accidentally return exception messages, headers, signatures, or payload text.
 */
public record WebhookVerificationResult(boolean verified, String reasonCode) {
    private static final Pattern REASON_CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,99}");

    public WebhookVerificationResult {
        Objects.requireNonNull(reasonCode, "reasonCode must not be null");
        reasonCode = reasonCode.trim();
        if (!REASON_CODE.matcher(reasonCode).matches()) {
            throw new IllegalArgumentException(
                    "reasonCode must be a stable uppercase token containing only A-Z, 0-9, and underscore");
        }
        if (verified && !reasonCode.equals("VERIFIED")) {
            throw new IllegalArgumentException("verified results must use reasonCode VERIFIED");
        }
        if (!verified && reasonCode.equals("VERIFIED")) {
            throw new IllegalArgumentException("unverified results must not use reasonCode VERIFIED");
        }
    }

    public static WebhookVerificationResult success() {
        return new WebhookVerificationResult(true, "VERIFIED");
    }

    public static WebhookVerificationResult rejected(String reasonCode) {
        if ("VERIFIED".equals(reasonCode)) {
            throw new IllegalArgumentException("rejection reason must not be VERIFIED");
        }
        return new WebhookVerificationResult(false, reasonCode);
    }
}
