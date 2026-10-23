package tech.kayys.numou.warehouse.application;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class WarehouseRecoveryWorkerTest {
    @Test
    void completedHandlerReleasesClaimAndReportsOutcome() {
        FakeRepository repository = new FakeRepository();
        repository.claims = List.of(claim());
        List<String> outcomes = new ArrayList<>();
        WarehouseRecoveryOperationsService service = new WarehouseRecoveryOperationsService(repository);
        try (WarehouseRecoveryWorker worker = worker(service, (claim) -> WarehouseRecoveryWorker.RecoveryResult.completed(),
                new Metrics(outcomes))) {
            assertEquals(1, worker.runOnce());
        }
        assertEquals(1, repository.releaseCalls);
        assertEquals(List.of("completed"), outcomes);
    }

    @Test
    void retryableFailureSchedulesDurableBackoff() {
        FakeRepository repository = new FakeRepository();
        repository.claims = List.of(claim());
        List<String> outcomes = new ArrayList<>();
        WarehouseRecoveryOperationsService service = new WarehouseRecoveryOperationsService(repository);
        try (WarehouseRecoveryWorker worker = worker(service,
                claim -> WarehouseRecoveryWorker.RecoveryResult.retryable("temporary outage"), new Metrics(outcomes))) {
            assertEquals(1, worker.runOnce());
        }
        assertNotNull(repository.scheduledAt);
        assertTrue(repository.scheduledAt.isAfter(Instant.now()));
        assertEquals(1, repository.scheduleCalls);
        assertEquals(List.of("retry_scheduled"), outcomes);
    }

    @Test
    void exhaustedAndNonRetryableFailuresAreDeadLettered() {
        FakeRepository repository = new FakeRepository();
        repository.claims = List.of(claim());
        List<String> outcomes = new ArrayList<>();
        WarehouseRecoveryOperationsService service = new WarehouseRecoveryOperationsService(repository);
        try (WarehouseRecoveryWorker worker = new WarehouseRecoveryWorker(service,
                new WarehouseRecoveryRetryPolicy(1, Duration.ofSeconds(1), Duration.ofMinutes(1), 2),
                claim -> WarehouseRecoveryWorker.RecoveryResult.retryable("exhausted"), new Metrics(outcomes),
                "worker-test", 10, Duration.ofSeconds(6), Duration.ofMillis(10))) {
            worker.runOnce();
        }
        assertEquals(1, repository.deadLetterCalls);
        assertEquals(List.of("dead_lettered"), outcomes);
    }

    @Test
    void validatesBoundsAndCloseStopsFuturePolling() {
        FakeRepository repository = new FakeRepository();
        WarehouseRecoveryOperationsService service = new WarehouseRecoveryOperationsService(repository);
        assertThrows(IllegalArgumentException.class, () -> new WarehouseRecoveryWorker(service,
                new WarehouseRecoveryRetryPolicy(3, Duration.ofSeconds(1), Duration.ofMinutes(1), 2),
                claim -> WarehouseRecoveryWorker.RecoveryResult.completed(), null,
                "worker", 501, Duration.ofSeconds(10), Duration.ofSeconds(1)));
        WarehouseRecoveryWorker worker = worker(service, claim -> WarehouseRecoveryWorker.RecoveryResult.completed(), null);
        worker.close();
        assertTrue(worker.isStopping());
        assertEquals(0, worker.runOnce());
        assertEquals(0, repository.claimCalls);
    }

    private static WarehouseRecoveryWorker worker(WarehouseRecoveryOperationsService service,
            WarehouseRecoveryWorker.RecoveryHandler handler, WarehouseRecoveryWorker.RecoveryMetrics metrics) {
        return new WarehouseRecoveryWorker(service,
                new WarehouseRecoveryRetryPolicy(4, Duration.ofMillis(20), Duration.ofSeconds(1), 2),
                handler, metrics, "worker-test", 10, Duration.ofSeconds(6), Duration.ofMillis(10));
    }

    private static WarehouseExecutionClaim claim() {
        WarehouseExecutionAttempt attempt = WarehouseExecutionAttempt.prepared(
                "tenant-test", "command-test", "fingerprint-test", "work-test", "line-test", "RECEIVE")
                .needsReconciliation("prior failure");
        return new WarehouseExecutionClaim(attempt, "worker-test", Instant.now().plusSeconds(6));
    }

    private static final class Metrics implements WarehouseRecoveryWorker.RecoveryMetrics {
        private final List<String> outcomes;
        private Metrics(List<String> outcomes) { this.outcomes = outcomes; }
        @Override public void outcome(String outcome) { outcomes.add(outcome); }
    }

    private static final class FakeRepository implements WarehouseExecutionAttemptRepository {
        List<WarehouseExecutionClaim> claims = List.of();
        int claimCalls;
        int releaseCalls;
        int scheduleCalls;
        int deadLetterCalls;
        Instant scheduledAt;

        @Override public WarehouseExecutionAttempt createIfAbsent(WarehouseExecutionAttempt attempt) { return attempt; }
        @Override public Optional<WarehouseExecutionAttempt> find(String tenantId, String commandKey) { return Optional.empty(); }
        @Override public WarehouseExecutionAttempt transition(String tenantId, String commandKey, long version,
                                                              WarehouseExecutionAttempt candidate) { return candidate; }
        @Override public List<WarehouseExecutionAttempt> findRecoverable(int limit) { return List.of(); }
        @Override public List<WarehouseExecutionClaim> claimRecoverable(String workerId, int limit, Duration lease) {
            claimCalls++;
            return claims;
        }
        @Override public WarehouseExecutionClaim renewClaim(WarehouseExecutionClaim claim, Duration lease) {
            return new WarehouseExecutionClaim(claim.attempt(), claim.workerId(), Instant.now().plus(lease));
        }
        @Override public void releaseClaim(WarehouseExecutionClaim claim) { releaseCalls++; }
        @Override public WarehouseExecutionAttempt scheduleRetry(WarehouseExecutionClaim claim, Instant nextEligibleAt) {
            scheduleCalls++;
            scheduledAt = nextEligibleAt;
            return claim.attempt();
        }
        @Override public WarehouseExecutionAttempt deadLetter(WarehouseExecutionClaim claim, String reason) {
            deadLetterCalls++;
            return claim.attempt();
        }
    }
}
