package tech.kayys.numou.queuemanagement.domain;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QueueOrderingTest {
    private static final Instant T0 = Instant.parse("2026-10-29T00:00:00Z");

    private final QueueDefinition queue = QueueDefinition.create(
            QueueId.of(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")),
            TenantId.of(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")),
            "Q1", "Queue", QueueProfile.WAITING_SERVICE, QueueOrderingPolicy.FIFO);

    private QueueEntry entry(String id, int priority, long offset) {
        return QueueEntry.join(QueueEntryId.of(UUID.fromString(id)), queue,
                new QueueSourceReference("vehicle-service", "work-order", id),
                priority, T0.plusSeconds(offset));
    }

    @Test
    void fifoUsesArrivalThenStableIdTieBreak() {
        QueueEntry later = entry("00000000-0000-0000-0000-000000000003", 10, 2);
        QueueEntry sameTimeHigherId = entry("00000000-0000-0000-0000-000000000002", 10, 1);
        QueueEntry sameTimeLowerId = entry("00000000-0000-0000-0000-000000000001", 10, 1);

        assertThat(QueueOrdering.orderWaiting(List.of(later, sameTimeHigherId, sameTimeLowerId), QueueOrderingPolicy.FIFO))
                .containsExactly(sameTimeLowerId, sameTimeHigherId, later);
    }

    @Test
    void rejectsCandidatesFromDifferentQueues() {
        QueueEntry first = entry("00000000-0000-0000-0000-000000000001", 10, 1);
        QueueDefinition otherQueue = QueueDefinition.create(QueueId.generate(), queue.tenantId(),
                "Q2", "Other queue", QueueProfile.WAITING_SERVICE, QueueOrderingPolicy.FIFO);
        QueueEntry other = QueueEntry.join(QueueEntryId.generate(), otherQueue,
                new QueueSourceReference("support", "ticket", "ticket-2"), 10, T0.plusSeconds(2));

        assertThatThrownBy(() -> QueueOrdering.orderWaiting(List.of(first, other), QueueOrderingPolicy.FIFO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("same tenant and queue");
    }

    @Test
    void excludesEntriesThatAreNoLongerWaiting() {
        QueueEntry waiting = entry("00000000-0000-0000-0000-000000000001", 10, 1);
        QueueEntry called = entry("00000000-0000-0000-0000-000000000002", 10, 2);
        called.call(T0.plusSeconds(3));

        assertThat(QueueOrdering.orderWaiting(List.of(waiting, called), QueueOrderingPolicy.FIFO))
                .containsExactly(waiting);
    }

    @Test
    void priorityPolicyUsesHigherPriorityThenArrival() {
        QueueEntry low = entry("00000000-0000-0000-0000-000000000003", 20, 1);
        QueueEntry highLater = entry("00000000-0000-0000-0000-000000000002", 90, 2);
        QueueEntry highEarlier = entry("00000000-0000-0000-0000-000000000001", 90, 1);

        assertThat(QueueOrdering.orderWaiting(List.of(low, highLater, highEarlier),
                QueueOrderingPolicy.PRIORITY_THEN_ARRIVAL))
                .containsExactly(highEarlier, highLater, low);
    }
}
