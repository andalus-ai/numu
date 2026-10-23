package tech.kayys.numou.warehouse.application;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Single-batch recovery poller. Scheduling and process lifecycle are owned by the host app.
 * Handler implementations must replay inventory/warehouse commands with their original
 * idempotency keys; the worker itself never fabricates a new command.
 *
 * <p>Failures are reported to a host-supplied listener so deployments can connect structured
 * logs, metrics, alerting, or a dead-letter/reconciliation workflow without coupling this
 * module to a particular observability stack.</p>
 */
public final class WarehouseExecutionRecoveryWorker {
    private final WarehouseExecutionAttemptRepository repository;
    private final RecoveryHandler handler;
    private final FailureListener failureListener;
    private final String workerId;
    private final int batchSize;
    private final Duration lease;

    public WarehouseExecutionRecoveryWorker(WarehouseExecutionAttemptRepository repository,
            RecoveryHandler handler, String workerId, int batchSize, Duration lease) {
        this(repository, handler, workerId, batchSize, lease, FailureListener.NOOP);
    }

    public WarehouseExecutionRecoveryWorker(WarehouseExecutionAttemptRepository repository,
            RecoveryHandler handler, String workerId, int batchSize, Duration lease,
            FailureListener failureListener) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.handler = Objects.requireNonNull(handler, "handler");
        this.failureListener = Objects.requireNonNull(failureListener, "failureListener");
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
                try {
                    failureListener.onFailure(new RecoveryFailure(
                            workerId, attempt.tenantId(), attempt.commandKey(), attempt.operation(),
                            attempt.status(), e));
                } catch (RuntimeException ignored) {
                    // Observability must not terminate processing of the rest of this claimed batch.
                }
            }
        }
        return new BatchResult(claims.size(), succeeded, failed);
    }

    @FunctionalInterface
    public interface RecoveryHandler {
        void recover(WarehouseExecutionAttempt attempt);
    }

    @FunctionalInterface
    public interface FailureListener {
        FailureListener NOOP = failure -> { };
        void onFailure(RecoveryFailure failure);
    }

    /** Structured failure context; exception is retained for logging/diagnostics by the host. */
    public record RecoveryFailure(String workerId, String tenantId, String commandKey,
            String operation, WarehouseExecutionAttemptStatus status, RuntimeException cause) {
        public RecoveryFailure {
            if (workerId == null || workerId.isBlank()) throw new IllegalArgumentException("workerId required");
            if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId required");
            if (commandKey == null || commandKey.isBlank()) throw new IllegalArgumentException("commandKey required");
            if (operation == null || operation.isBlank()) throw new IllegalArgumentException("operation required");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(cause, "cause");
        }
    }

    public record BatchResult(int claimed, int succeeded, int failed) {
        public BatchResult {
            if (claimed < 0 || succeeded < 0 || failed < 0 || succeeded + failed != claimed)
                throw new IllegalArgumentException("Invalid recovery batch counts");
        }
    }
}
