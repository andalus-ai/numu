package tech.kayys.numou.queuemanagement.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.queuemanagement.domain.QueueDefinition;
import tech.kayys.numou.queuemanagement.domain.QueueEntry;
import tech.kayys.numou.queuemanagement.domain.QueueEntryId;
import tech.kayys.numou.queuemanagement.domain.QueueId;
import tech.kayys.numou.queuemanagement.domain.QueueOrderingPolicy;
import tech.kayys.numou.queuemanagement.domain.QueueProfile;
import tech.kayys.numou.queuemanagement.domain.QueueSourceReference;
import tech.kayys.numou.queuemanagement.spi.ClaimMode;
import tech.kayys.numou.queuemanagement.spi.QueueCommandContext;
import tech.kayys.numou.queuemanagement.spi.QueueCommandResult;
import tech.kayys.numou.queuemanagement.spi.QueueCommandTransactionPort;
import tech.kayys.numou.queuemanagement.spi.QueueRepositoryPort;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class QueueCommandServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-29T00:00:00Z");

    @Test
    void joinPersistsEntryAndReturnsAuditAndVersionedEventForTransaction() {
        TenantId tenant = TenantId.generate();
        QueueId queueId = QueueId.generate();
        FakeRepository repository = new FakeRepository();
        repository.addQueue(QueueDefinition.create(queueId, tenant, "FRONT-DESK", "Front desk",
                QueueProfile.WAITING_SERVICE, QueueOrderingPolicy.FIFO));
        DirectTransaction transaction = new DirectTransaction();
        QueueCommandService service = new QueueCommandService(repository, transaction);
        QueueCommandContext context = context(tenant, "join-1");

        QueueEntry entry = service.join(context, queueId,
                new QueueSourceReference("customer", "visit", "visit-123"), 10);

        assertEquals(tenant, entry.tenantId());
        assertEquals(queueId, entry.queueId());
        assertSame(entry, repository.entries.get(entry.id()));
        assertEquals(1, transaction.lastResult.auditRecords().size());
        assertEquals(1, transaction.lastResult.events().size());
    }

    @Test
    void claimNextRejectsProfileMismatchBeforeRepositoryClaim() {
        TenantId tenant = TenantId.generate();
        QueueId queueId = QueueId.generate();
        FakeRepository repository = new FakeRepository();
        repository.addQueue(QueueDefinition.create(queueId, tenant, "OPS", "Operations",
                QueueProfile.OPERATIONAL_WORK, QueueOrderingPolicy.FIFO));
        QueueCommandService service = new QueueCommandService(repository, new DirectTransaction());

        assertThrows(IllegalStateException.class,
                () -> service.claimNext(context(tenant, "claim-1"), queueId, ClaimMode.CALL_SERVICE_ENTRY));
        assertEquals(0, repository.claimCount);
    }

    @Test
    void transitionCannotLoadAnEntryAcrossTenantScope() {
        TenantId tenantA = TenantId.generate();
        TenantId tenantB = TenantId.generate();
        QueueId queueId = QueueId.generate();
        FakeRepository repository = new FakeRepository();
        QueueDefinition queue = QueueDefinition.create(queueId, tenantB, "B-QUEUE", "Tenant B",
                QueueProfile.WAITING_SERVICE, QueueOrderingPolicy.FIFO);
        repository.addQueue(queue);
        QueueEntry entry = QueueEntry.join(QueueEntryId.generate(), queue,
                new QueueSourceReference("customer", "visit", "b-visit"), 0, NOW);
        repository.entries.put(entry.id(), entry);
        QueueCommandService service = new QueueCommandService(repository, new DirectTransaction());

        assertThrows(IllegalArgumentException.class, () -> service.transition(
                context(tenantA, "transition-1"), queueId, entry.id(), QueueEntryAction.ABANDON));
    }

    private static QueueCommandContext context(TenantId tenant, String key) {
        return new QueueCommandContext(tenant, "staff-1", "corr-" + key, key, NOW);
    }

    private static final class DirectTransaction implements QueueCommandTransactionPort {
        private QueueCommandResult<?> lastResult;
        @Override public <T> T execute(QueueCommandContext context, String scope,
                                      Supplier<QueueCommandResult<T>> work) {
            QueueCommandResult<T> result = work.get();
            lastResult = result;
            return result.value();
        }
    }

    private static final class FakeRepository implements QueueRepositoryPort {
        private final Map<QueueId, QueueDefinition> queues = new HashMap<>();
        private final Map<QueueEntryId, QueueEntry> entries = new HashMap<>();
        private int claimCount;
        void addQueue(QueueDefinition queue) { queues.put(queue.id(), queue); }
        @Override public Optional<QueueDefinition> findQueue(TenantId tenant, QueueId id) {
            QueueDefinition queue = queues.get(id);
            return queue != null && queue.tenantId().equals(tenant) ? Optional.of(queue) : Optional.empty();
        }
        @Override public Optional<QueueEntry> findEntry(TenantId tenant, QueueId queue, QueueEntryId id) {
            QueueEntry entry = entries.get(id);
            return entry != null && entry.tenantId().equals(tenant) && entry.queueId().equals(queue)
                    ? Optional.of(entry) : Optional.empty();
        }
        @Override public List<QueueEntry> findWaiting(TenantId tenant, QueueId queue) {
            return entries.values().stream().filter(e -> e.tenantId().equals(tenant)
                    && e.queueId().equals(queue)
                    && e.status() == tech.kayys.numou.queuemanagement.domain.QueueEntryStatus.WAITING).toList();
        }
        @Override public void saveQueue(QueueDefinition queue) { queues.put(queue.id(), queue); }
        @Override public void saveEntry(QueueEntry entry) { entries.put(entry.id(), entry); }
        @Override public QueueEntry claimNext(TenantId tenant, QueueId queue, ClaimMode mode,
                                              String actor, Instant now) {
            claimCount++;
            throw new UnsupportedOperationException("Not needed by this test");
        }
    }
}