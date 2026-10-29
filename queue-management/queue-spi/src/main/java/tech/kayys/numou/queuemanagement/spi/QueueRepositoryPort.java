package tech.kayys.numou.queuemanagement.spi;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.queuemanagement.domain.QueueDefinition;
import tech.kayys.numou.queuemanagement.domain.QueueEntry;
import tech.kayys.numou.queuemanagement.domain.QueueEntryId;
import tech.kayys.numou.queuemanagement.domain.QueueId;

import java.util.List;
import java.util.Optional;

/**
 * Tenant-scoped persistence contract. Implementations must include tenant_id in every
 * lookup/update predicate; filtering in application memory is not an isolation boundary.
 */
public interface QueueRepositoryPort {
    Optional<QueueDefinition> findQueue(TenantId tenantId, QueueId queueId);

    Optional<QueueEntry> findEntry(TenantId tenantId, QueueId queueId, QueueEntryId entryId);

    List<QueueEntry> findWaiting(TenantId tenantId, QueueId queueId);

    void saveQueue(QueueDefinition queue);

    void saveEntry(QueueEntry entry);

    /**
     * Atomically selects and claims the next eligible entry for this tenant and queue.
     * The implementation must serialize competing claims (e.g. row lock or compare-and-set),
     * apply the queue's configured ordering, persist the lifecycle transition, and return
     * the persisted winner. It must never select outside the supplied tenant/queue.
     */
    QueueEntry claimNext(TenantId tenantId, QueueId queueId, ClaimMode mode,
                         String actorId, java.time.Instant now);
}