package tech.kayys.numou.queuemanagement.spi;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/** Tenant-scoped durable lease operations and audited dead-letter recovery. */
public interface QueueOutboxDeliveryPort {
    List<QueueOutboxMessage> claim(UUID tenantId, String workerId, int limit, Duration leaseDuration);
    boolean acknowledge(UUID tenantId, UUID eventId, UUID leaseToken);
    RetryOutcome retry(UUID tenantId, UUID eventId, UUID leaseToken, Duration delay,
                       int maxAttempts, String error);

    /** Returns a bounded newest-first page; callers must request at most 200 rows. */
    List<QueueDeadLetterMessage> listDeadLetters(UUID tenantId, int limit);

    /** Requeues one terminal event and writes its operator audit record atomically. */
    boolean replayDeadLetter(UUID tenantId, UUID eventId, String actorId,
                             String correlationId, String reason);

    enum RetryOutcome { RESCHEDULED, DEAD_LETTERED, LEASE_LOST }
}
