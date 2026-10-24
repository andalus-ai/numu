package tech.kayys.numou.omnichannel.core;

import java.time.Instant;
import java.util.List;

/** Persistence port for tenant-scoped inspection and audited replay. */
public interface DeadLetterOperations {
    List<DeadLetterEvent> find(DeadLetterQuery query);

    /**
     * Atomically moves one matching DEAD_LETTER row to RETRYABLE_FAILURE and
     * records the operator audit entry. Implementations must scope by all key
     * fields including tenant and must not reset the event's identity or attempts.
     */
    DeadLetterReplayResult replay(
            InboundEventKey key,
            String actorId,
            String reason,
            Instant requestedAt);
}
