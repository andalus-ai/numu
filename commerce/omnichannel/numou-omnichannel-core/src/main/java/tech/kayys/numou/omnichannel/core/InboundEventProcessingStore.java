package tech.kayys.numou.omnichannel.core;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Durable worker/lifecycle port for the inbound inbox.
 *
 * <p>Implementations must make claims and lifecycle changes atomic. Claiming
 * increments the attempt count and installs a lease. Expired PROCESSING leases
 * may be reclaimed after a worker crash. Business dispatch must remain
 * idempotent because a worker can crash after committing a side effect.</p>
 */
public interface InboundEventProcessingStore {

    /** Claims at most one eligible item, or returns empty when none is due. */
    Optional<InboundWorkItem> claimNext(Instant now, Instant leaseUntil, int maxAttempts);

    /** Compare-and-set PROCESSING to PROCESSED. */
    boolean markProcessed(InboundEventKey key, Instant completedAt);

    /**
     * Records a sanitized error code and schedules a retry, or dead-letters
     * the item when its attempt budget has been exhausted.
     */
    boolean recordFailure(InboundEventKey key, String errorCode, Instant retryAt, int maxAttempts);

    /** Moves any eligible, exhausted records to DEAD_LETTER. */
    int deadLetterExhausted(Instant now, int maxAttempts);

    /** Read-only query for operator tooling; this does not replay events. */
    List<InboundWorkItem> findDeadLetters(int limit);
}
