package tech.kayys.numou.warehouse.application;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WarehouseExecutionRecoveryWorkerTest {
    @Test void successfulRecoveryReleasesClaim() {
        FakeRepository repository = new FakeRepository();
        WarehouseExecutionRecoveryWorker worker = new WarehouseExecutionRecoveryWorker(
                repository, attempt -> { }, "worker-a", 10, Duration.ofSeconds(30));
        var result = worker.runOnce();
        assertEquals(1, result.claimed());
        assertEquals(1, result.succeeded());
        assertEquals(0, result.failed());
        assertEquals(1, repository.released);
    }

    @Test void failedRecoveryLeavesLeaseToExpireAndReportsStructuredFailure() {
        FakeRepository repository = new FakeRepository();
        List<WarehouseExecutionRecoveryWorker.RecoveryFailure> failures = new ArrayList<>();
        WarehouseExecutionRecoveryWorker worker = new WarehouseExecutionRecoveryWorker(
                repository, attempt -> { throw new IllegalStateException("temporary failure"); },
                "worker-a", 10, Duration.ofSeconds(30), failures::add);
        var result = worker.runOnce();
        assertEquals(1, result.failed());
        assertEquals(0, repository.released);
        assertEquals(1, failures.size());
        var failure = failures.get(0);
        assertEquals("worker-a", failure.workerId());
        assertEquals("tenant", failure.tenantId());
        assertEquals("command", failure.commandKey());
        assertEquals("receive", failure.operation());
        assertEquals(WarehouseExecutionAttemptStatus.PREPARED, failure.status());
        assertEquals("temporary failure", failure.cause().getMessage());
    }

    @Test void brokenFailureListenerDoesNotAbortBatchAccounting() {
        FakeRepository repository = new FakeRepository();
        WarehouseExecutionRecoveryWorker worker = new WarehouseExecutionRecoveryWorker(
                repository, attempt -> { throw new IllegalStateException("recovery failed"); },
                "worker-a", 10, Duration.ofSeconds(30), failure -> {
                    throw new IllegalStateException("logging backend unavailable");
                });
        var result = assertDoesNotThrow(worker::runOnce);
        assertEquals(new WarehouseExecutionRecoveryWorker.BatchResult(1, 0, 1), result);
        assertEquals(0, repository.released);
    }

    @Test void validatesBatchAndLease() {
        FakeRepository repository = new FakeRepository();
        assertThrows(IllegalArgumentException.class, () -> new WarehouseExecutionRecoveryWorker(
                repository, attempt -> { }, "worker-a", 0, Duration.ofSeconds(30)));
        assertThrows(IllegalArgumentException.class, () -> new WarehouseExecutionRecoveryWorker(
                repository, attempt -> { }, "worker-a", 1, Duration.ZERO));
        assertThrows(NullPointerException.class, () -> new WarehouseExecutionRecoveryWorker(
                repository, attempt -> { }, "worker-a", 1, Duration.ofSeconds(30), null));
    }

    private static final class FakeRepository implements WarehouseExecutionAttemptRepository {
        int released;
        WarehouseExecutionAttempt attempt = WarehouseExecutionAttempt.prepared(
                "tenant", "command", "fingerprint", "work", "line", "receive");
        @Override public WarehouseExecutionAttempt createIfAbsent(WarehouseExecutionAttempt a) { return attempt; }
        @Override public Optional<WarehouseExecutionAttempt> find(String t, String k) { return Optional.of(attempt); }
        @Override public WarehouseExecutionAttempt transition(String t, String k, long v, WarehouseExecutionAttempt c) { attempt=c; return c; }
        @Override public List<WarehouseExecutionAttempt> findRecoverable(int limit) { return List.of(attempt); }
        @Override public List<WarehouseExecutionClaim> claimRecoverable(String worker, int limit, Duration lease) {
            return List.of(new WarehouseExecutionClaim(attempt, worker, java.time.Instant.now().plus(lease)));
        }
        @Override public void releaseClaim(String tenant, String command, String worker) { released++; }
    }
}
