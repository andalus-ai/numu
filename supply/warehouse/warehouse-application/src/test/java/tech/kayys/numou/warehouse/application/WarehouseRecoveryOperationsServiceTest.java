package tech.kayys.numou.warehouse.application;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class WarehouseRecoveryOperationsServiceTest {
    @Test
    void validatesClaimBoundsBeforeCallingRepository() {
        FakeRepository repository = new FakeRepository();
        WarehouseRecoveryOperationsService service = new WarehouseRecoveryOperationsService(repository);
        assertThrows(IllegalArgumentException.class, () -> service.claim("worker-a", 0, Duration.ofSeconds(5)));
        assertThrows(IllegalArgumentException.class, () -> service.claim("worker-a", 1, Duration.ZERO));
        assertEquals(0, repository.claimCalls);
    }

    @Test
    void delegatesBoundedClaimAndCopiesResults() {
        FakeRepository repository = new FakeRepository();
        repository.claims = List.of(claim());
        WarehouseRecoveryOperationsService service = new WarehouseRecoveryOperationsService(repository);
        assertEquals(repository.claims, service.claim(" worker-a ", 10, Duration.ofSeconds(30)));
        assertEquals(1, repository.claimCalls);
    }

    @Test
    void retryDeferralRequiresFutureTimeAndUsesClaimFencedOperation() {
        FakeRepository repository = new FakeRepository();
        WarehouseExecutionClaim claim = claim();
        WarehouseRecoveryOperationsService service = new WarehouseRecoveryOperationsService(repository);
        assertThrows(IllegalArgumentException.class, () -> service.defer(claim, Instant.EPOCH));
        Instant next = Instant.now().plusSeconds(30);
        assertEquals(repository.scheduled, service.defer(claim, next));
        assertEquals(claim, repository.scheduledClaim);
        assertEquals(next, repository.scheduledAt);
    }

    @Test
    void deadLetterRequiresReasonAndDelegatesClaimFencedOperation() {
        FakeRepository repository = new FakeRepository();
        WarehouseExecutionClaim claim = claim();
        WarehouseRecoveryOperationsService service = new WarehouseRecoveryOperationsService(repository);
        assertThrows(IllegalArgumentException.class, () -> service.deadLetter(claim, " "));
        assertEquals(repository.dead, service.deadLetter(claim, " exhausted "));
        assertEquals(claim, repository.deadClaim);
        assertEquals("exhausted", repository.deadReason);
    }

    @Test
    void deadLetterQueryIsBounded() {
        FakeRepository repository = new FakeRepository();
        WarehouseRecoveryOperationsService service = new WarehouseRecoveryOperationsService(repository);
        assertThrows(IllegalArgumentException.class, () -> service.deadLetters(1001));
        assertEquals(0, repository.deadLetterQueryCalls);
    }

    private static WarehouseExecutionClaim claim() {
        WarehouseExecutionAttempt attempt = WarehouseExecutionAttempt.prepared(
                "tenant-a", "cmd-a", "fp-a", "work-a", "line-a", "RECEIVE")
                .needsReconciliation("timeout");
        return new WarehouseExecutionClaim(attempt, "worker-a", Instant.now().plusSeconds(30));
    }

    private static final class FakeRepository implements WarehouseExecutionAttemptRepository {
        int claimCalls;
        int deadLetterQueryCalls;
        List<WarehouseExecutionClaim> claims = List.of();
        WarehouseExecutionClaim scheduledClaim;
        Instant scheduledAt;
        WarehouseExecutionAttempt scheduled;
        WarehouseExecutionClaim deadClaim;
        String deadReason;
        WarehouseExecutionAttempt dead;
        @Override public WarehouseExecutionAttempt createIfAbsent(WarehouseExecutionAttempt attempt) { return attempt; }
        @Override public Optional<WarehouseExecutionAttempt> find(String tenantId, String commandKey) { return Optional.empty(); }
        @Override public WarehouseExecutionAttempt transition(String tenantId, String commandKey, long version,
                                                              WarehouseExecutionAttempt candidate) { return candidate; }
        @Override public List<WarehouseExecutionAttempt> findRecoverable(int limit) { return List.of(); }
        @Override public List<WarehouseExecutionClaim> claimRecoverable(String workerId, int limit, Duration lease) {
            claimCalls++;
            return claims;
        }
        @Override public WarehouseExecutionAttempt scheduleRetry(WarehouseExecutionClaim claim, Instant nextEligibleAt) {
            scheduledClaim = claim;
            scheduledAt = nextEligibleAt;
            scheduled = claim.attempt();
            return scheduled;
        }
        @Override public WarehouseExecutionAttempt deadLetter(WarehouseExecutionClaim claim, String reason) {
            deadClaim = claim;
            deadReason = reason;
            dead = claim.attempt();
            return dead;
        }
        @Override public List<WarehouseExecutionAttempt> findDeadLetters(int limit) {
            deadLetterQueryCalls++;
            return List.of();
        }
    }
}
