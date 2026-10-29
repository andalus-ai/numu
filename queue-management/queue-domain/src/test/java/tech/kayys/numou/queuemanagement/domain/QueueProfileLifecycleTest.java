package tech.kayys.numou.queuemanagement.domain;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class QueueProfileLifecycleTest {
    private static final Instant T0 = Instant.parse("2026-10-29T00:00:00Z");

    private QueueDefinition queue(QueueProfile profile) {
        return QueueDefinition.create(QueueId.generate(), TenantId.generate(), "Q1", "Queue",
                profile, QueueOrderingPolicy.FIFO);
    }

    private QueueSourceReference source() {
        return new QueueSourceReference("courier", "delivery-task", "task-123");
    }

    @Test
    void dispatchOfferCanBeRejectedAndRequeuedOrAcceptedAndCompleted() {
        QueueEntry rejected = QueueEntry.join(QueueEntryId.generate(), queue(QueueProfile.DISPATCH),
                source(), 50, T0);
        assertThatIllegalStateException().isThrownBy(() -> rejected.call(T0.plusSeconds(1)));

        rejected.offer(T0.plusSeconds(1));
        rejected.rejectOffer(T0.plusSeconds(2));
        assertThat(rejected.status()).isEqualTo(QueueEntryStatus.REJECTED);
        rejected.requeue(T0.plusSeconds(3));
        assertThat(rejected.status()).isEqualTo(QueueEntryStatus.WAITING);

        rejected.offer(T0.plusSeconds(4));
        rejected.acceptOffer(T0.plusSeconds(5));
        rejected.completeDispatch(T0.plusSeconds(6));
        assertThat(rejected.status()).isEqualTo(QueueEntryStatus.COMPLETED);
        assertThat(rejected.isTerminal()).isTrue();
    }

    @Test
    void operationalQueueOnlyResolvesItsEntryAfterSourceReportsCompletion() {
        QueueSourceReference source = source();
        QueueEntry entry = QueueEntry.join(QueueEntryId.generate(), queue(QueueProfile.OPERATIONAL_WORK),
                source, 20, T0);

        assertThatIllegalStateException().isThrownBy(() -> entry.complete(T0.plusSeconds(1)));
        entry.resolveFromSource(T0.plusSeconds(1));

        assertThat(entry.status()).isEqualTo(QueueEntryStatus.COMPLETED);
        assertThat(entry.source()).isEqualTo(source);
    }

    @Test
    void serviceOperationsCannotBeUsedOnDispatchEntries() {
        QueueEntry entry = QueueEntry.join(QueueEntryId.generate(), queue(QueueProfile.DISPATCH),
                source(), 50, T0);
        assertThatIllegalStateException().isThrownBy(() -> entry.startServing(T0.plusSeconds(1)));
    }
}
