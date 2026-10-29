package tech.kayys.numou.queuemanagement.domain;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class QueueEntryTest {
    private static final Instant T0 = Instant.parse("2026-10-29T00:00:00Z");

    private QueueDefinition activeQueue() {
        return QueueDefinition.create(QueueId.generate(), TenantId.generate(), "Q1", "Queue",
                QueueProfile.WAITING_SERVICE, QueueOrderingPolicy.FIFO);
    }

    private QueueEntry entry(int priority) {
        return QueueEntry.join(QueueEntryId.generate(), activeQueue(),
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
    void reconstitutesPersistedLifecycleWithoutResettingState() {
        TenantId tenantId = TenantId.generate();
        QueueId queueId = QueueId.generate();
        QueueEntryId entryId = QueueEntryId.generate();
        Instant calledAt = T0.plusSeconds(1);
        Instant servingAt = T0.plusSeconds(2);
        Instant completedAt = T0.plusSeconds(3);

        QueueEntry restored = QueueEntry.reconstitute(entryId, tenantId, queueId,
                QueueProfile.WAITING_SERVICE,
                new QueueSourceReference("support", "ticket", "ticket-123"),
                42, T0, completedAt, QueueEntryStatus.COMPLETED,
                calledAt, servingAt, completedAt, 2);

        assertThat(restored.id()).isEqualTo(entryId);
        assertThat(restored.tenantId()).isEqualTo(tenantId);
        assertThat(restored.queueId()).isEqualTo(queueId);
        assertThat(restored.priority()).isEqualTo(42);
        assertThat(restored.status()).isEqualTo(QueueEntryStatus.COMPLETED);
        assertThat(restored.enteredAt()).isEqualTo(T0);
        assertThat(restored.updatedAt()).isEqualTo(completedAt);
        assertThat(restored.calledAt()).isEqualTo(calledAt);
        assertThat(restored.servingStartedAt()).isEqualTo(servingAt);
        assertThat(restored.completedAt()).isEqualTo(completedAt);
        assertThat(restored.callCount()).isEqualTo(2);
        assertThat(restored.isTerminal()).isTrue();
    }

    @Test
    void rejectsInvalidPersistedLifecycleState() {
        TenantId tenantId = TenantId.generate();
        QueueId queueId = QueueId.generate();
        QueueSourceReference source = new QueueSourceReference("support", "ticket", "ticket-123");

        assertThatIllegalArgumentException().isThrownBy(() ->
                QueueEntry.reconstitute(QueueEntryId.generate(), tenantId, queueId,
                        QueueProfile.WAITING_SERVICE, source, 10, T0, T0.minusSeconds(1),
                        QueueEntryStatus.WAITING, null, null, null, 0));
        assertThatIllegalArgumentException().isThrownBy(() ->
                QueueEntry.reconstitute(QueueEntryId.generate(), tenantId, queueId,
                        QueueProfile.WAITING_SERVICE, source, 10, T0, T0,
                        QueueEntryStatus.OFFERED, null, null, null, 0));
        assertThatIllegalArgumentException().isThrownBy(() ->
                QueueEntry.reconstitute(QueueEntryId.generate(), tenantId, queueId,
                        QueueProfile.WAITING_SERVICE, source, 10, T0, T0,
                        QueueEntryStatus.WAITING, null, null, null, -1));
    }

    @Test
    void rejectsEntriesForPausedOrClosedQueues() {
        QueueDefinition queue = activeQueue();
        queue.pause();
        assertThatIllegalStateException().isThrownBy(() -> QueueEntry.join(QueueEntryId.generate(), queue,
                new QueueSourceReference("support", "ticket", "ticket-123"), 10, T0));
    }

    @Test
    void rejectsInvalidPriorityAndNonMonotonicTimes() {
        assertThatIllegalArgumentException().isThrownBy(() -> entry(101));
        QueueEntry entry = entry(0);
        assertThatIllegalArgumentException().isThrownBy(() -> entry.call(T0.minusSeconds(1)));
    }
}
