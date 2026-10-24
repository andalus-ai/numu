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
}
