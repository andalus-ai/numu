package tech.kayys.numou.omnichannel.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class IdempotencyKeyTest {

    @Test
    void trimsValueAndPreservesCase() {
        assertThat(new IdempotencyKey("  Provider-Key-1  ").value()).isEqualTo("Provider-Key-1");
    }

    @Test
    void rejectsBlankAndOverlongKeys() {
        assertThatIllegalArgumentException().isThrownBy(() -> new IdempotencyKey("  "));
        assertThatIllegalArgumentException().isThrownBy(() -> new IdempotencyKey("x".repeat(201)));
    }
}
