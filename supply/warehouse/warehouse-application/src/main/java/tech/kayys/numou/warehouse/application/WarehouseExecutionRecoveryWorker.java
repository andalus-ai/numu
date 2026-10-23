package tech.kayys.numou.warehouse.application;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Single-batch recovery poller. Scheduling and process lifecycle are owned by the host app.
 * Handler implementations must replay inventory/warehouse commands with their original
 * idempotency keys; the worker itself never fabricates a new command.
 */
public final class WarehouseExecutionRecoveryWorker {
    private final WarehouseExecutionAttemptRepository repository;
    private final RecoveryHandler handler;
    private final String workerId;
    private final int batchSize;
    private final Duration lease;

    public WarehouseExecutionRecoveryWorker(WarehouseExecutionAttemptRepository repository,
            RecoveryHandler handler, String workerId, int batchSize, Duration lease) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.handler = Objects.requireNonNull(handler, "handler");
        if (workerId == null || workerId.isBlank()) throw new IllegalArgumentException("workerId required");
        if (batchSize < 1 || batchSize > 500) throw new IllegalArgumentException("batchSize must be 1..500");
        if (lease == null || lease.isZero() || lease.isNegative() || lease.compareTo(Duration.ofHours(1)) > 0)
            throw new IllegalArgumentException("lease must be positive and at most one hour");
        this.workerId = workerId.trim();
        this.batchSize = batchSize;
        this.lease = lease;
    }

    /** Claims one bounded batch and attempts each item once; failures remain leased until expiry. */
    public BatchResult runOnce() {
        List<WarehouseExecutionClaim> claims = repository.claimRecoverable(workerId, batchSize, lease);
        int succeeded = 0;
        int failed = 0;
        for (WarehouseExecutionClaim claim : claims) {
            WarehouseExecutionAttempt attempt = claim.attempt();
            try {
                handler.recover(attempt);
                repository.releaseClaim(attempt.tenantId(), attempt.commandKey(), workerId);
                succeeded++;
            } catch (RuntimeException e) {
                // Keep the lease on failure. Expiry makes it recoverable without a hot retry loop.
                failed++;
            }
        }
        return new BatchResult(claims.size(), succeeded, failed);
    }

    @FunctionalInterface
    public interface RecoveryHandler {
        void recover(WarehouseExecutionAttempt attempt);
    }

    public record BatchResult(int claimed, int succeeded, int failed) {
        public BatchResult {
            if (claimed < 0 || succeeded < 0 || failed < 0 || succeeded + failed != claimed)
                throw new IllegalArgumentException("Invalid recovery batch counts");
        }
    }
}
