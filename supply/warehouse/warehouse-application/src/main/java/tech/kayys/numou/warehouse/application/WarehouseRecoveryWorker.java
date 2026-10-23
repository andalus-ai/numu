package tech.kayys.numou.warehouse.application;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Runtime-neutral, bounded polling worker for warehouse recovery.
 *
 * <p>The handler must be idempotent and persist successful domain/journal changes before
 * returning {@link RecoveryResult#completed()}. The worker owns claim lifecycle, lease
 * renewal, retry scheduling, dead-lettering, shutdown release, and operational counters.
 * No framework scheduler or application container is required.</p>
 *
 * <p>Run {@link #runOnce()} from a scheduler of the caller's choice, or use
 * {@link #runUntilStopped(Duration)} for a simple managed polling loop.</p>
 */
public final class WarehouseRecoveryWorker implements AutoCloseable {
    private final WarehouseRecoveryOperationsService operations;
    private final WarehouseRecoveryRetryPolicy retryPolicy;
    private final RecoveryHandler handler;
    private final RecoveryMetrics metrics;
    private final String workerId;
    private final int batchSize;
    private final Duration lease;
    private final Duration pollInterval;
    private final ScheduledExecutorService renewer;
    private final AtomicBoolean stopping = new AtomicBoolean();

    public WarehouseRecoveryWorker(
            WarehouseRecoveryOperationsService operations,
            WarehouseRecoveryRetryPolicy retryPolicy,
            RecoveryHandler handler,
            RecoveryMetrics metrics,
            String workerId,
            int batchSize,
            Duration lease,
            Duration pollInterval) {
        this.operations = Objects.requireNonNull(operations, "operations");
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy");
        this.handler = Objects.requireNonNull(handler, "handler");
        this.metrics = metrics == null ? RecoveryMetrics.NOOP : metrics;
        if (workerId == null || workerId.isBlank()) throw new IllegalArgumentException("workerId is required");
        if (batchSize < 1 || batchSize > 500) throw new IllegalArgumentException("batchSize must be between 1 and 500");
        if (lease == null || lease.compareTo(Duration.ofSeconds(3)) < 0 || lease.compareTo(Duration.ofHours(1)) > 0)
            throw new IllegalArgumentException("lease must be between 3 seconds and one hour");
        if (pollInterval == null || pollInterval.isNegative() || pollInterval.isZero())
            throw new IllegalArgumentException("pollInterval must be positive");
        this.workerId = workerId.trim();
        this.batchSize = batchSize;
        this.lease = lease;
        this.pollInterval = pollInterval;
        this.renewer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "warehouse-recovery-lease-renewer-" + this.workerId);
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Claims and processes at most batchSize attempts. Returns the number claimed. */
    public int runOnce() {
        if (stopping.get()) return 0;
        List<WarehouseExecutionClaim> claims = operations.claim(workerId, batchSize, lease);
        metrics.claimed(claims.size());
        int claimed = claims.size();
        for (int i = 0; i < claims.size(); i++) {
            WarehouseExecutionClaim claim = claims.get(i);
            if (stopping.get()) {
                releaseQuietly(claim);
                for (int j = i + 1; j < claims.size(); j++) releaseQuietly(claims.get(j));
                break;
            }
            process(claim);
        }
        return claimed;
    }

    /** Polls until close() is called; interruptions preserve the thread interrupt flag. */
    public void runUntilStopped() {
        while (!stopping.get()) {
            try {
                runOnce();
                if (!stopping.get()) TimeUnit.NANOSECONDS.sleep(pollInterval.toNanos());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                close();
            } catch (RuntimeException pollingFailure) {
                metrics.pollFailure(pollingFailure);
                if (!stopping.get()) {
                    try {
                        TimeUnit.NANOSECONDS.sleep(pollInterval.toNanos());
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        close();
                    }
                }
            }
        }
    }

    private void process(WarehouseExecutionClaim originalClaim) {
        AtomicReference<WarehouseExecutionClaim> currentClaim = new AtomicReference<>(originalClaim);
        AtomicBoolean leaseLost = new AtomicBoolean();
        long renewalEveryMillis = Math.max(1L, lease.toMillis() / 3L);
        var heartbeat = renewer.scheduleAtFixedRate(() -> {
            if (stopping.get() || leaseLost.get()) return;
            try {
                currentClaim.set(operationsRenew(currentClaim.get()));
                metrics.leaseRenewed();
            } catch (RuntimeException lost) {
                leaseLost.set(true);
                metrics.leaseLost(lost);
            }
        }, renewalEveryMillis, renewalEveryMillis, TimeUnit.MILLISECONDS);

        try {
            RecoveryResult result;
            try {
                result = Objects.requireNonNull(handler.recover(originalClaim), "handler result");
            } catch (Exception failure) {
                result = RecoveryResult.retryable(messageFor(failure));
            }
            if (leaseLost.get()) {
                metrics.outcome("lease_lost");
                return; // The old worker is fenced out; never mutate or release its stale claim.
            }
            WarehouseExecutionClaim liveClaim = currentClaim.get();
            if (stopping.get()) {
                releaseQuietly(liveClaim);
                metrics.outcome("stopped");
                return;
            }
            switch (result.outcome()) {
                case COMPLETED -> {
                    operations.release(liveClaim);
                    metrics.outcome("completed");
                }
                case RETRYABLE_FAILURE -> scheduleRetryOrDeadLetter(liveClaim, result.reason(), true);
                case NON_RETRYABLE_FAILURE -> scheduleRetryOrDeadLetter(liveClaim, result.reason(), false);
            }
        } catch (RuntimeException operationalFailure) {
            metrics.processingFailure(operationalFailure);
            if (!leaseLost.get()) releaseQuietly(currentClaim.get());
        } finally {
            heartbeat.cancel(false);
        }
    }

    private WarehouseExecutionClaim operationsRenew(WarehouseExecutionClaim claim) {
        // Lease renewal remains a repository operation exposed through the service facade.
        return operations.renew(claim, lease);
    }

    private void scheduleRetryOrDeadLetter(WarehouseExecutionClaim claim, String reason, boolean retryable) {
        Instant failedAt = Instant.now();
        WarehouseRecoveryRetryPolicy.RetryDecision decision = retryable
                ? retryPolicy.decide(claim.attempt().attemptCount(), failedAt)
                : new WarehouseRecoveryRetryPolicy.RetryDecision(false, java.util.Optional.empty(), reason);
        if (!decision.retryAllowed()) {
            String terminalReason = decision.terminalReason() == null ? reason : decision.terminalReason();
            operations.deadLetter(claim, terminalReason);
            metrics.outcome("dead_lettered");
            return;
        }
        operations.defer(claim, decision.nextEligibleAt().orElseThrow());
        metrics.retryScheduled(decision.nextEligibleAt().orElseThrow());
        metrics.outcome("retry_scheduled");
    }

    private void releaseQuietly(WarehouseExecutionClaim claim) {
        try {
            operations.release(claim);
        } catch (RuntimeException failure) {
            metrics.releaseFailure(failure);
        }
    }

    private static String messageFor(Exception failure) {
        String message = failure.getMessage();
        String value = failure.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }

    @Override
    public void close() {
        if (stopping.compareAndSet(false, true)) renewer.shutdownNow();
    }

    public boolean isStopping() {
        return stopping.get();
    }

    @FunctionalInterface
    public interface RecoveryHandler {
        /** Perform idempotent recovery and durably persist success before returning completed. */
        RecoveryResult recover(WarehouseExecutionClaim claim) throws Exception;
    }

    public record RecoveryResult(Outcome outcome, String reason) {
        public RecoveryResult {
            Objects.requireNonNull(outcome, "outcome");
            if (outcome == Outcome.COMPLETED && reason != null)
                throw new IllegalArgumentException("Completed result cannot have a failure reason");
            if (outcome != Outcome.COMPLETED && (reason == null || reason.isBlank()))
                throw new IllegalArgumentException("Failure outcomes require a reason");
            if (reason != null && reason.length() > 1000) reason = reason.substring(0, 1000);
        }
        public static RecoveryResult completed() { return new RecoveryResult(Outcome.COMPLETED, null); }
        public static RecoveryResult retryable(String reason) { return new RecoveryResult(Outcome.RETRYABLE_FAILURE, reason); }
        public static RecoveryResult nonRetryable(String reason) { return new RecoveryResult(Outcome.NON_RETRYABLE_FAILURE, reason); }
    }

    public enum Outcome { COMPLETED, RETRYABLE_FAILURE, NON_RETRYABLE_FAILURE }

    /** Dependency-free metrics hook; adapters may bridge these callbacks to Micrometer/OpenTelemetry. */
    public interface RecoveryMetrics {
        RecoveryMetrics NOOP = new RecoveryMetrics() {};
        default void claimed(int count) {}
        default void leaseRenewed() {}
        default void leaseLost(RuntimeException failure) {}
        default void retryScheduled(Instant nextEligibleAt) {}
        default void outcome(String outcome) {}
        default void pollFailure(RuntimeException failure) {}
        default void processingFailure(RuntimeException failure) {}
        default void releaseFailure(RuntimeException failure) {}
    }
}
