package tech.kayys.numou.omnichannel.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InboundPayloadReferenceTest {
    @Test
    void trimsOpaqueReference() {
        assertThat(new InboundPayloadReference(" payload-123 ").value()).isEqualTo("payload-123");
    }

    @Test
    void rejectsBlankReference() {
        assertThatThrownBy(() -> new InboundPayloadReference("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
