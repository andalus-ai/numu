package tech.kayys.numou.omnichannel.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InboundDeliveryStateTest {

    @Test
    void allowsVerificationProcessingAndRetryPath() {
        assertThat(InboundDeliveryState.RECEIVED.canTransitionTo(InboundDeliveryState.VERIFIED)).isTrue();
        assertThat(InboundDeliveryState.VERIFIED.canTransitionTo(InboundDeliveryState.PROCESSING)).isTrue();
        assertThat(InboundDeliveryState.PROCESSING.canTransitionTo(InboundDeliveryState.RETRYABLE_FAILURE)).isTrue();
        assertThat(InboundDeliveryState.RETRYABLE_FAILURE.canTransitionTo(InboundDeliveryState.PROCESSING)).isTrue();
        assertThat(InboundDeliveryState.PROCESSING.canTransitionTo(InboundDeliveryState.PROCESSED)).isTrue();
    }

    @Test
    void rejectsIllegalTransitionsAndMakesTerminalStatesImmutable() {
        assertThat(InboundDeliveryState.RECEIVED.canTransitionTo(InboundDeliveryState.PROCESSED)).isFalse();
        assertThat(InboundDeliveryState.PROCESSED.canTransitionTo(InboundDeliveryState.PROCESSING)).isFalse();
        assertThat(InboundDeliveryState.REJECTED.canTransitionTo(InboundDeliveryState.VERIFIED)).isFalse();
        assertThat(InboundDeliveryState.DEAD_LETTER.isTerminal()).isTrue();
        assertThat(InboundDeliveryState.RETRYABLE_FAILURE.isTerminal()).isFalse();
    }
}
