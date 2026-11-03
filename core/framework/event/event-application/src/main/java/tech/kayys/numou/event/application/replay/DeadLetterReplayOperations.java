package tech.kayys.numou.event.application.replay;
import java.time.Duration;import java.time.Instant;import java.util.Optional;import java.util.UUID;
/** Durable replay state port. Capture, claim and outcome changes must be transactionally persisted. */
public interface DeadLetterReplayOperations {
 boolean capture(DeadLetterReplayRecord record);
 Optional<DeadLetterReplayClaim> claim(UUID tenantId,DeadLetterSourceKey source,String actorId,String reason,Instant requestedAt,Duration leaseDuration);
 boolean markPublished(UUID tenantId,DeadLetterSourceKey source,UUID claimToken,Instant completedAt);
 boolean markFailed(UUID tenantId,DeadLetterSourceKey source,UUID claimToken,String safeFailure,Instant failedAt);
}