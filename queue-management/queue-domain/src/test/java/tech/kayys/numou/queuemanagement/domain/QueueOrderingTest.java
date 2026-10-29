package tech.kayys.numou.queuemanagement.domain;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class QueueOrderingTest {
    private static final Instant T0 = Instant.parse("2026-10-29T00:00:00Z");

    private QueueEntry entry(String id, int priority, long offset) {
        return QueueEntry.join(QueueEntryId.of(UUID.fromString(id)), TenantId.of(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")),
                QueueId.of(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")),
                new QueueSourceReference("vehicle-service", "work-order", id),
                priority, T0.plusSeconds(offset));
    }

    @Test
    void fifoUsesArrivalThenStableIdTieBreak() {
        QueueEntry later = entry("00000000-0000-0000-0000-000000000002", 10, 2);
        QueueEntry sameTimeHigherId = entry("00000000-0000-0000-0000-000000000002", 10, 1);
        QueueEntry sameTimeLowerId = entry("00000000-0000-0000-0000-000000000001", 10, 1);

        assertThat(QueueOrdering.order(List.of(later, sameTimeHigherId, sameTimeLowerId), QueueOrderingPolicy.FIFO))
                .containsExactly(sameTimeLowerId, sameTimeHigherId, later);
    }

    @Test
    void priorityPolicyUsesHigherPriorityThenArrival() {
        QueueEntry low = entry("00000000-0000-0000-0000-000000000003", 20, 1);
        QueueEntry highLater = entry("00000000-0000-0000-0000-000000000002", 90, 2);
        QueueEntry highEarlier = entry("00000000-0000-0000-0000-000000000001", 90, 1);

        assertThat(QueueOrdering.order(List.of(low, highLater, highEarlier),
                QueueOrderingPolicy.PRIORITY_THEN_ARRIVAL))
                .containsExactly(highEarlier, highLater, low);
    }
}
