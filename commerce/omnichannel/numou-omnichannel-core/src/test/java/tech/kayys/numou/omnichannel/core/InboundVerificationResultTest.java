package tech.kayys.numou.omnichannel.core;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InboundVerificationResultTest {
    private static final InboundPayloadReference REFERENCE = new InboundPayloadReference("payload-opaque-1");

    @Test
    void successfulDispositionRequiresPayloadReference() {
        assertThat(new InboundVerificationResult(
                InboundVerificationDisposition.VERIFIED, Optional.of(REFERENCE)).isVerified()).isTrue();
        assertThat(new InboundVerificationResult(
                InboundVerificationDisposition.ALREADY_VERIFIED, Optional.of(REFERENCE)).isVerified()).isTrue();
    }

    @Test
    void unsuccessfulDispositionCannotExposePayloadReference() {
        assertThat(new InboundVerificationResult(
                InboundVerificationDisposition.DIGEST_MISMATCH, Optional.empty()).isVerified()).isFalse();
        assertThatThrownBy(() -> new InboundVerificationResult(
                InboundVerificationDisposition.NOT_FOUND, Optional.of(REFERENCE)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
