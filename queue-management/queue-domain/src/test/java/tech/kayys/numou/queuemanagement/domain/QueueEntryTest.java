package tech.kayys.numou.queuemanagement.domain;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class QueueEntryTest {
    private static final Instant T0 = Instant.parse("2026-10-29T00:00:00Z");

    private QueueEntry entry(int priority) {
        return QueueEntry.join(QueueEntryId.generate(), TenantId.generate(), QueueId.generate(),
                new QueueSourceReference("support", "ticket", "ticket-123"), priority, T0);
    }

    @Test
    void enforcesServiceLifecycleAndRecordsTransitions() {
        QueueEntry entry = entry(50);
        assertThat(entry.status()).isEqualTo(QueueEntryStatus.WAITING);
        assertThatIllegalStateException().isThrownBy(() -> entry.startServing(T0));

        entry.call(T0.plusSeconds(1));
        assertThat(entry.callCount()).isEqualTo(1);
        entry.startServing(T0.plusSeconds(2));
        entry.complete(T0.plusSeconds(3));

        assertThat(entry.status()).isEqualTo(QueueEntryStatus.COMPLETED);
        assertThat(entry.calledAt()).isEqualTo(T0.plusSeconds(1));
        assertThat(entry.servingStartedAt()).isEqualTo(T0.plusSeconds(2));
        assertThat(entry.completedAt()).isEqualTo(T0.plusSeconds(3));
        assertThat(entry.isTerminal()).isTrue();
        assertThatIllegalStateException().isThrownBy(() -> entry.call(T0.plusSeconds(4)));
    }

    @Test
    void supportsNoShowRequeueAndAbandonment() {
        QueueEntry entry = entry(10);
        entry.call(T0.plusSeconds(1));
        entry.markNoShow(T0.plusSeconds(2));
        assertThat(entry.status()).isEqualTo(QueueEntryStatus.NO_SHOW);
        entry.requeue(T0.plusSeconds(3));
        assertThat(entry.status()).isEqualTo(QueueEntryStatus.WAITING);
        entry.abandon(T0.plusSeconds(4));
        assertThat(entry.status()).isEqualTo(QueueEntryStatus.ABANDONED);
    }

    @Test
    void rejectsInvalidPriorityAndNonMonotonicTimes() {
        assertThatIllegalArgumentException().isThrownBy(() -> entry(101));
        QueueEntry entry = entry(0);
        assertThatIllegalArgumentException().isThrownBy(() -> entry.call(T0.minusSeconds(1)));
    }
}
