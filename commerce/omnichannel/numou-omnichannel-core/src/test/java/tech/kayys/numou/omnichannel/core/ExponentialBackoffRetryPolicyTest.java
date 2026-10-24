package tech.kayys.numou.omnichannel.core;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
class ExponentialBackoffRetryPolicyTest {
    private static final Instant NOW = Instant.parse("2026-10-24T00:00:00Z");
    @Test void growsExponentiallyAndCapsAtMaximum() {
        var policy = new ExponentialBackoffRetryPolicy(Duration.ofSeconds(2), Duration.ofSeconds(10), 0.0);
        assertThat(policy.delayFor(item(1))).isEqualTo(Duration.ofSeconds(2));
        assertThat(policy.delayFor(item(2))).isEqualTo(Duration.ofSeconds(4));
        assertThat(policy.delayFor(item(3))).isEqualTo(Duration.ofSeconds(8));
        assertThat(policy.delayFor(item(4))).isEqualTo(Duration.ofSeconds(10));
        assertThat(policy.delayFor(item(20))).isEqualTo(Duration.ofSeconds(10));
    }
    @Test void jitterIsStableAndBounded() {
        var policy = new ExponentialBackoffRetryPolicy(Duration.ofSeconds(10), Duration.ofMinutes(1), 0.2);
        Duration first = policy.delayFor(item(2));
        assertThat(policy.delayFor(item(2))).isEqualTo(first);
        assertThat(first).isBetween(Duration.ofSeconds(16), Duration.ofSeconds(24));
    }
    @Test void rejectsInvalidConfiguration() {
        assertThatThrownBy(() -> new ExponentialBackoffRetryPolicy(Duration.ZERO, Duration.ofSeconds(1), 0.1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExponentialBackoffRetryPolicy(Duration.ofSeconds(2), Duration.ofSeconds(1), 0.1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExponentialBackoffRetryPolicy(Duration.ofSeconds(1), Duration.ofSeconds(2), 0.6))
                .isInstanceOf(IllegalArgumentException.class);
    }
    private static InboundWorkItem item(int attempt) {
        return new InboundWorkItem(new InboundEventKey(TenantId.of("00000000-0000-0000-0000-000000000001"),
                "provider", "account-1", "event-1"), "order.updated", "0".repeat(64),
                ChannelType.MARKETPLACE, Optional.empty(), "corr-1", NOW, attempt, NOW.plusSeconds(30),
                Optional.of("00000000-0000-0000-0000-000000000099"));
    }
}
