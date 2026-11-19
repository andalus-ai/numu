package tech.kayys.numou.content.spi;

import tech.kayys.numou.content.domain.TenantId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface EditorialOutboxRepository {
    /** Leases pending rows using SKIP LOCKED; claim commits before any external workflow call. */
    List<EditorialOutboxMessage> claim(UUID workerId, Instant now, Instant leaseUntil, int limit);
    boolean markPublished(TenantId tenantId, UUID eventId, UUID workerId, Instant publishedAt);
    boolean releaseForRetry(TenantId tenantId, UUID eventId, UUID workerId, Instant retryAt, String error);
}
