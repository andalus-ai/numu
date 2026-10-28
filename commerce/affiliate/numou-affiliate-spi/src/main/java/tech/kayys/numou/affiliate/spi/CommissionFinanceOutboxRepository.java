package tech.kayys.numou.affiliate.spi;

import tech.kayys.numou.affiliate.domain.CommissionFinancePosting;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Durable affiliate-owned outbox. Enqueue must be atomic/idempotent on tenantId + idempotencyKey.
 * Claim must use a lease so concurrent workers cannot process the same row simultaneously.
 * Expired leases must become claimable again after worker failure.
 */
public interface CommissionFinanceOutboxRepository {
    CompletionStage<CommissionFinancePosting> enqueueOrGet(CommissionFinancePosting posting);

    CompletionStage<List<ClaimedPosting>> claimPending(
            String workerId, Instant now, Duration leaseDuration, int limit);

    CompletionStage<CommissionFinancePosting> complete(
            String tenantId, UUID postingId, String leaseToken, String externalReference, Instant completedAt);

    CompletionStage<CommissionFinancePosting> recordRetryableFailure(
            String tenantId, UUID postingId, String leaseToken, String reason, Instant failedAt);

    CompletionStage<CommissionFinancePosting> requireReconciliation(
            String tenantId, UUID postingId, String leaseToken, String reason, Instant at);

    CompletionStage<Optional<CommissionFinancePosting>> findByIdempotencyKey(String tenantId, String idempotencyKey);

    record ClaimedPosting(CommissionFinancePosting posting, String leaseToken) {
        public ClaimedPosting {
            if (posting == null) throw new IllegalArgumentException("posting is required");
            if (leaseToken == null || leaseToken.isBlank()) throw new IllegalArgumentException("leaseToken is required");
        }
    }
}
