package tech.kayys.numou.numouapplication.port;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.numouapplication.ops.OutboxRecord;

import java.time.Instant;
import java.util.List;

/**
 * Relay-side view of the transactional outbox. Must be called inside a unit of
 * work.
 */
public interface OutboxPort {

    /**
     * Locks and returns up to {@code limit} unpublished events in publication order
     * (concurrent relays skip them).
     */
    List<OutboxRecord> claim(TenantId tenant, int limit);

    void markPublished(TenantId tenant, List<Long> sequences, Instant at);
}
