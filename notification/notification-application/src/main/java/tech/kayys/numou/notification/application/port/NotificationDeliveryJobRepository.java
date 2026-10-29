package tech.kayys.numou.notification.application.port;

import tech.kayys.numou.notification.domain.NotificationDeliveryJob;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Durable queue contract; adapters must atomically claim and fence transitions by lease token. */
public interface NotificationDeliveryJobRepository {
    /** Repeated tenant/idempotency keys must not create duplicate provider deliveries. */
    NotificationDeliveryJob enqueue(NotificationDeliveryJob job);

    /** Atomically claims due jobs and expired leases across concurrent workers. */
    List<NotificationDeliveryJob> claimDue(Instant now, Instant leaseExpiry, int limit, UUID leaseToken);

    Optional<NotificationDeliveryJob> markDelivered(UUID tenantId, UUID jobId, UUID leaseToken, Instant now);
    Optional<NotificationDeliveryJob> scheduleRetry(
            UUID tenantId, UUID jobId, UUID leaseToken, Instant retryAt, String safeErrorCode);
    Optional<NotificationDeliveryJob> markDead(
            UUID tenantId, UUID jobId, UUID leaseToken, String safeErrorCode);
}
