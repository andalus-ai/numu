package tech.kayys.numou.feedback.application.outbox;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Durable lease-based storage contract for the Feedback transactional outbox. */
public interface FeedbackOutboxStore {
    List<FeedbackOutboxMessage> claimPending(Instant now, Instant leaseUntil, int limit);
    void markPublished(UUID eventId, UUID leaseToken, Instant publishedAt);
    void recordFailure(UUID eventId, UUID leaseToken, String error, Instant nextAttemptAt, int maximumAttempts);
}
