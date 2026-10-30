package tech.kayys.numou.queuemanagement.spi;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/** Tenant-scoped durable lease operations for at-least-once outbox delivery. */
public interface QueueOutboxDeliveryPort {
    List<QueueOutboxMessage> claim(UUID tenantId, String workerId, int limit, Duration leaseDuration);
    boolean acknowledge(UUID tenantId, UUID eventId, UUID leaseToken);
    RetryOutcome retry(UUID tenantId, UUID eventId, UUID leaseToken, Duration delay,
                       int maxAttempts, String error);

    enum RetryOutcome { RESCHEDULED, DEAD_LETTERED, LEASE_LOST }
}
