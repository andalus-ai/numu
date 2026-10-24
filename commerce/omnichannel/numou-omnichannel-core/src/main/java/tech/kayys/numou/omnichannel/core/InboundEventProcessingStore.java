package tech.kayys.numou.omnichannel.core;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Durable worker/lifecycle port for the inbound inbox.
 *
 * <p>Implementations must make claims and lifecycle changes atomic. Claiming
 * increments the attempt count and installs a lease token. Expired PROCESSING
 * leases may be reclaimed after a worker crash. Business dispatch must remain
 * idempotent because a worker can crash after committing a side effect.</p>
 */
public interface InboundEventProcessingStore {

    /** Claims at most one eligible item, or returns empty when none is due. */
    Optional<InboundWorkItem> claimNext(Instant now, Instant leaseUntil, int maxAttempts);

    /** Compare-and-set PROCESSING to PROCESSED using the lease token. */
    boolean markProcessed(InboundWorkItem item, Instant completedAt);

    /** Records a sanitized error and schedules retry/dead-letter using the lease token. */
    boolean recordFailure(InboundWorkItem item, String errorCode, Instant retryAt, int maxAttempts);

    /** Moves any eligible, exhausted records to DEAD_LETTER. */
    int deadLetterExhausted(Instant now, int maxAttempts);

    /** Read-only query for operator tooling; returned items are not leased. */
    List<InboundWorkItem> findDeadLetters(int limit);
}
