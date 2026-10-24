package tech.kayys.numou.omnichannel.core;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InboundWorkItemTest {

    private static InboundEventKey key() {
        return new InboundEventKey(
                TenantId.of("00000000-0000-0000-0000-000000000001"),
                "provider", "account-1", "event-1");
    }

    @Test
    void claimedItemMustHaveLeaseTokenAndPositiveAttemptCount() {
        InboundWorkItem item = new InboundWorkItem(
                key(), "order.updated", "a".repeat(64), ChannelType.MARKETPLACE,
                Optional.empty(), "corr-1", Instant.parse("2026-01-01T00:00:00Z"),
                1, Instant.parse("2026-01-01T00:01:00Z"), Optional.of("lease-1"));

        assertThat(item.isLeased()).isTrue();
    }

    @Test
    void deadLetterSummaryCanBeUnleased() {
        InboundWorkItem item = new InboundWorkItem(
                key(), "order.updated", "a".repeat(64), ChannelType.MARKETPLACE,
                Optional.empty(), "corr-1", Instant.parse("2026-01-01T00:00:00Z"),
                0, Instant.parse("2026-01-01T00:01:00Z"), Optional.empty());

        assertThat(item.isLeased()).isFalse();
    }

    @Test
    void leasedItemCannotHaveZeroAttempts() {
        assertThatThrownBy(() -> new InboundWorkItem(
                key(), "order.updated", "a".repeat(64), ChannelType.MARKETPLACE,
                Optional.empty(), "corr-1", Instant.parse("2026-01-01T00:00:00Z"),
                0, Instant.parse("2026-01-01T00:01:00Z"), Optional.of("lease-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
