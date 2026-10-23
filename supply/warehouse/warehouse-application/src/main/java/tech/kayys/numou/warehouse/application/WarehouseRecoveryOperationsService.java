package tech.kayys.numou.warehouse.application;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Operational facade for recovery workers.
 *
 * <p>Retry scheduling and dead-lettering are delegated to atomic repository operations
 * that verify the exact live claim and relinquish it in the same write. A worker must
 * discard a claim after either terminal operation succeeds.</p>
 */
public final class WarehouseRecoveryOperationsService {
    private final WarehouseExecutionAttemptRepository repository;

    public WarehouseRecoveryOperationsService(WarehouseExecutionAttemptRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public List<WarehouseExecutionClaim> claim(String workerId, int limit, Duration lease) {
        if (workerId == null || workerId.isBlank()) throw new IllegalArgumentException("workerId is required");
        if (limit < 1 || limit > 500) throw new IllegalArgumentException("limit must be between 1 and 500");
        if (lease == null || lease.isZero() || lease.isNegative() || lease.compareTo(Duration.ofHours(1)) > 0) {
            throw new IllegalArgumentException("lease must be positive and no longer than one hour");
        }
        return List.copyOf(repository.claimRecoverable(workerId.trim(), limit, lease));
    }

    /** Persist backoff and release the exact claim atomically. */
    public WarehouseExecutionAttempt defer(WarehouseExecutionClaim claim, Instant nextEligibleAt) {
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(nextEligibleAt, "nextEligibleAt");
        if (nextEligibleAt.isBefore(Instant.now())) {
            throw new IllegalArgumentException("nextEligibleAt must not be in the past");
        }
        return repository.scheduleRetry(claim, nextEligibleAt);
    }

    /** Mark an exhausted/non-retryable reconciliation attempt terminal and release its claim atomically. */
    public WarehouseExecutionAttempt deadLetter(WarehouseExecutionClaim claim, String reason) {
        Objects.requireNonNull(claim, "claim");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("reason is required");
        return repository.deadLetter(claim, reason.trim());
    }

    public List<WarehouseExecutionAttempt> deadLetters(int limit) {
        if (limit < 1 || limit > 1000) throw new IllegalArgumentException("limit must be between 1 and 1000");
        return List.copyOf(repository.findDeadLetters(limit));
    }

    /** Renew the exact current claim; callers must replace their old fencing token. */
    public WarehouseExecutionClaim renew(WarehouseExecutionClaim claim, Duration lease) {
        Objects.requireNonNull(claim, "claim");
        if (lease == null || lease.isZero() || lease.isNegative()
                || lease.compareTo(Duration.ofHours(1)) > 0) {
            throw new IllegalArgumentException("lease must be positive and no longer than one hour");
        }
        return repository.renewClaim(claim, lease);
    }

    public void release(WarehouseExecutionClaim claim) {
        repository.releaseClaim(Objects.requireNonNull(claim, "claim"));
    }
}
