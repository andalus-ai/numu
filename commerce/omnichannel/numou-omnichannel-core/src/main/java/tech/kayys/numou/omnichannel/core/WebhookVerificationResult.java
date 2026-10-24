package tech.kayys.numou.omnichannel.core;

import java.util.Objects;

/** Provider verification outcome with a non-sensitive machine-readable reason. */
public record WebhookVerificationResult(boolean verified, String reasonCode) {
    public WebhookVerificationResult {
        Objects.requireNonNull(reasonCode, "reasonCode must not be null");
        reasonCode = reasonCode.trim();
        if (reasonCode.isEmpty() || reasonCode.length() > 100) {
            throw new IllegalArgumentException("reasonCode must contain 1 to 100 characters");
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
