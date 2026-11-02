package tech.kayys.numou.event.application.replay;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Durable replay state port. A claim implementation must atomically persist
 * the source identity, tenant, actor/reason audit, lease and fencing token.
 * Unexpired claims must not be granted to another operator/process.
 */
public interface DeadLetterReplayOperations {
    Optional<DeadLetterReplayClaim> claim(
            UUID tenantId,
            DeadLetterSourceKey source,
            String actorId,
            String reason,
            Instant requestedAt,
            Duration leaseDuration);

    /** Must update only when the supplied fencing token still owns the claim. */
    boolean markPublished(UUID tenantId, DeadLetterSourceKey source, UUID claimToken, Instant completedAt);

    /** Must update only when the supplied fencing token still owns the claim. */
    boolean markFailed(UUID tenantId, DeadLetterSourceKey source, UUID claimToken, String safeFailure, Instant failedAt);
}
