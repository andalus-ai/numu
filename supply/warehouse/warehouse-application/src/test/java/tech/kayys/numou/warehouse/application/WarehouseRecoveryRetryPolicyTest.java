package tech.kayys.numou.warehouse.application;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class WarehouseRecoveryRetryPolicyTest {
    private final WarehouseRecoveryRetryPolicy policy = new WarehouseRecoveryRetryPolicy(
            4, Duration.ofSeconds(5), Duration.ofSeconds(20), 2);

    @Test
    void schedulesCappedExponentialBackoffFromFailureTime() {
        Instant failedAt = Instant.parse("2026-10-24T10:00:00Z");

        assertEquals(failedAt.plusSeconds(5), policy.decide(1, failedAt).nextEligibleAt().orElseThrow());
        assertEquals(failedAt.plusSeconds(10), policy.decide(2, failedAt).nextEligibleAt().orElseThrow());
        assertEquals(failedAt.plusSeconds(20), policy.decide(3, failedAt).nextEligibleAt().orElseThrow());
    }

    @Test
    void exhaustedAttemptHasNoRetryTimeAndExplainsTerminalDecision() {
        var decision = policy.decide(4, Instant.parse("2026-10-24T10:00:00Z"));

        assertFalse(decision.retryAllowed());
        assertTrue(decision.nextEligibleAt().isEmpty());
        assertTrue(decision.terminalReason().contains("Maximum recovery attempts reached"));
    }

    @Test
    void capsOverflowingBackoffAndSupportsSingleAttemptPolicies() {
        var capped = new WarehouseRecoveryRetryPolicy(8, Duration.ofDays(10), Duration.ofDays(20), 10);
        assertEquals(Duration.ofDays(20), capped.backoffFor(2));
        var once = new WarehouseRecoveryRetryPolicy(1, Duration.ofSeconds(1), Duration.ofSeconds(1), 2);
        assertFalse(once.decide(1, Instant.EPOCH).retryAllowed());
    }

    @Test
    void rejectsInvalidPolicyAndAttemptInputs() {
        assertThrows(IllegalArgumentException.class, () -> new WarehouseRecoveryRetryPolicy(
                0, Duration.ofSeconds(1), Duration.ofSeconds(2), 2));
        assertThrows(IllegalArgumentException.class, () -> new WarehouseRecoveryRetryPolicy(
                3, Duration.ZERO, Duration.ofSeconds(2), 2));
        assertThrows(IllegalArgumentException.class, () -> new WarehouseRecoveryRetryPolicy(
                3, Duration.ofSeconds(3), Duration.ofSeconds(2), 2));
        assertThrows(IllegalArgumentException.class, () -> new WarehouseRecoveryRetryPolicy(
                3, Duration.ofSeconds(1), Duration.ofSeconds(2), 0));
        assertThrows(IllegalArgumentException.class, () -> policy.decide(0, Instant.EPOCH));
        assertThrows(NullPointerException.class, () -> policy.decide(1, null));
    }
}
