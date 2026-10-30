package tech.kayys.numou.queuemanagement.postgres;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.queuemanagement.domain.*;
import tech.kayys.numou.queuemanagement.spi.ClaimMode;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(QueuePostgresQuarkusTestResource.class)
class QueuePostgresRepositoryQuarkusTest {
    private static final TenantId TENANT = TenantId.of(
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));

    @Inject QueuePostgresRepository repository;

    @Test
    void persistsAndReconstitutesQueueAndEntry() {
        QueueId queueId = QueueId.of(UUID.randomUUID());
        QueueEntryId entryId = QueueEntryId.of(UUID.randomUUID());
        QueueDefinition queue = QueueDefinition.create(queueId, TENANT,
                "q-" + queueId.value().toString().substring(0, 8), "Integration Queue",
                QueueProfile.WAITING_SERVICE, QueueOrderingPolicy.FIFO);
        Instant enteredAt = Instant.parse("2026-10-30T01:00:00Z");
        QueueEntry entry = QueueEntry.join(entryId, queue,
                new QueueSourceReference("hospitality", "appointment", "appointment-" + entryId.value()),
                40, enteredAt);

        repository.saveQueue(queue);
        repository.saveEntry(entry);

        assertEquals(queue.id(), repository.findQueue(TENANT, queueId).orElseThrow().id());
        QueueEntry loaded = repository.findEntry(TENANT, queueId, entryId).orElseThrow();
        assertEquals(entry.id(), loaded.id());
        assertEquals(entry.source(), loaded.source());
        assertEquals(40, loaded.priority());
        assertEquals(QueueEntryStatus.WAITING, loaded.status());
        assertEquals(enteredAt, loaded.enteredAt());
        assertEquals(0, loaded.callCount());
    }

    @Test
    void tenantScopedQueriesCannotReadAnotherTenantsQueueOrEntries() {
        QueueId queueId = QueueId.of(UUID.randomUUID());
        QueueEntryId entryId = QueueEntryId.of(UUID.randomUUID());
        QueueDefinition queue = QueueDefinition.create(queueId, TENANT,
                "q-" + queueId.value().toString().substring(0, 8), "Tenant Queue",
                QueueProfile.WAITING_SERVICE, QueueOrderingPolicy.FIFO);
        QueueEntry entry = QueueEntry.join(entryId, queue,
                new QueueSourceReference("test", "ticket", "ticket-" + entryId.value()), 0, Instant.now());
        repository.saveQueue(queue);
        repository.saveEntry(entry);

        TenantId otherTenant = TenantId.of(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));
        assertTrue(repository.findQueue(otherTenant, queueId).isEmpty());
        assertTrue(repository.findEntry(otherTenant, queueId, entryId).isEmpty());
        assertTrue(repository.findWaiting(otherTenant, queueId).isEmpty());
    }

    @Test
    void claimNextUsesPriorityOrderingAndPersistsTheClaimTransition() {
        QueueId queueId = QueueId.of(UUID.randomUUID());
        QueueDefinition queue = QueueDefinition.create(queueId, TENANT,
                "q-" + queueId.value().toString().substring(0, 8), "Priority Queue",
                QueueProfile.WAITING_SERVICE, QueueOrderingPolicy.PRIORITY_THEN_ARRIVAL);
        Instant enteredAt = Instant.parse("2026-10-30T01:00:00Z");
        QueueEntry low = QueueEntry.join(QueueEntryId.of(UUID.randomUUID()), queue,
                new QueueSourceReference("test", "ticket", "low-" + UUID.randomUUID()), 10, enteredAt);
        QueueEntry high = QueueEntry.join(QueueEntryId.of(UUID.randomUUID()), queue,
                new QueueSourceReference("test", "ticket", "high-" + UUID.randomUUID()), 90,
                enteredAt.plusSeconds(1));
        repository.saveQueue(queue);
        repository.saveEntry(low);
        repository.saveEntry(high);

        assertEquals(2, repository.findWaiting(TENANT, queueId).size());
        QueueEntry claimed = repository.claimNext(TENANT, queueId, ClaimMode.CALL_SERVICE_ENTRY,
                "queue-operator", enteredAt.plusSeconds(10));

        assertEquals(high.id(), claimed.id());
        assertEquals(QueueEntryStatus.CALLED, claimed.status());
        assertEquals(1, claimed.callCount());
        QueueEntry persisted = repository.findEntry(TENANT, queueId, high.id()).orElseThrow();
        assertEquals(QueueEntryStatus.CALLED, persisted.status());
        assertEquals(1, persisted.callCount());
        assertEquals(1, repository.findWaiting(TENANT, queueId).size());
    }
}
