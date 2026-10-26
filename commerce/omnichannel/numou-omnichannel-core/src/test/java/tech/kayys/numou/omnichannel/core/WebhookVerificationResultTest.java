package tech.kayys.numou.omnichannel.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookVerificationResultTest {
    @Test
    void successUsesStableVerifiedCode() {
        assertThat(WebhookVerificationResult.success())
                .isEqualTo(new WebhookVerificationResult(true, "VERIFIED"));
    }

    @Test
    void rejectsInconsistentSuccessAndReasonCode() {
        assertThatThrownBy(() -> new WebhookVerificationResult(true, "BAD_SIGNATURE"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WebhookVerificationResult.rejected("VERIFIED"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsStableUppercaseMachineReadableReasonCodes() {
        assertThat(WebhookVerificationResult.rejected("INVALID_SIGNATURE").reasonCode())
                .isEqualTo("INVALID_SIGNATURE");
        assertThat(WebhookVerificationResult.rejected("REPLAY_PROTECTION_UNAVAILABLE").reasonCode())
                .isEqualTo("REPLAY_PROTECTION_UNAVAILABLE");
    }

    @Test
    void rejectsFreeTextAndPotentiallySensitiveReasonCodes() {
        assertThatThrownBy(() -> WebhookVerificationResult.rejected("signature invalid"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WebhookVerificationResult.rejected("INVALID_SIGNATURE: secret=abc"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WebhookVerificationResult.rejected("INVALID-SIGNATURE"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WebhookVerificationResult.rejected("a".repeat(101)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void trimsReasonCodeBeforeValidating() {
        assertThat(WebhookVerificationResult.rejected(" INVALID_SIGNATURE ").reasonCode())
                .isEqualTo("INVALID_SIGNATURE");
    }
}
