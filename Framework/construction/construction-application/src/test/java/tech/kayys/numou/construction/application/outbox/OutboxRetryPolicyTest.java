package tech.kayys.numou.construction.application.outbox;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class OutboxRetryPolicyTest {
    private final OutboxRetryPolicy policy = new OutboxRetryPolicy(
            Duration.ofSeconds(2), Duration.ofSeconds(10), 4);
    private final Instant t0 = Instant.parse("2026-01-01T00:00:00Z");

    @Test void appliesCappedExponentialDelay() {
        assertEquals(t0.plusSeconds(2), policy.nextAttemptAt(t0, 1));
        assertEquals(t0.plusSeconds(4), policy.nextAttemptAt(t0, 2));
        assertEquals(t0.plusSeconds(8), policy.nextAttemptAt(t0, 3));
        assertEquals(t0.plusSeconds(10), policy.nextAttemptAt(t0, 4));
        assertEquals(t0.plusSeconds(10), policy.nextAttemptAt(t0, 8));
    }

    @Test void marksAttemptsAtOrAboveLimitTerminal() {
        assertFalse(policy.isTerminal(3));
        assertTrue(policy.isTerminal(4));
        assertTrue(policy.isTerminal(5));
    }

    @Test void rejectsInvalidAttempts() {
        assertThrows(IllegalArgumentException.class, () -> policy.nextAttemptAt(t0, 0));
        assertThrows(IllegalArgumentException.class, () -> policy.isTerminal(-1));
    }
}
