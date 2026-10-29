package tech.kayys.numou.notification.application;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

class NotificationDeliveryRetryPolicyTest {
    @Test void exponentialDelayIsCappedAndAttemptLimitIsEnforced() {
        var policy = new NotificationDeliveryRetryPolicy(4, Duration.ofSeconds(2), Duration.ofSeconds(10), 0);
        assertEquals(Duration.ofSeconds(2), policy.delayForAttempt(1));
        assertEquals(Duration.ofSeconds(4), policy.delayForAttempt(2));
        assertEquals(Duration.ofSeconds(8), policy.delayForAttempt(3));
        assertEquals(Duration.ofSeconds(10), policy.delayForAttempt(4));
        assertFalse(policy.exhausted(3));
        assertTrue(policy.exhausted(4));
    }
    @Test void jitterRemainsWithinConfiguredBounds() {
        var policy = new NotificationDeliveryRetryPolicy(3, Duration.ofSeconds(10), Duration.ofSeconds(30), 0.2);
        for (int i = 0; i < 100; i++) {
            Duration delay = policy.delayForAttempt(1);
            assertTrue(delay.compareTo(Duration.ofSeconds(8)) >= 0);
            assertTrue(delay.compareTo(Duration.ofSeconds(12)) <= 0);
        }
    }
    @Test void rejectsInvalidPolicy() {
        assertThrows(IllegalArgumentException.class, () -> new NotificationDeliveryRetryPolicy(0, Duration.ofSeconds(1), Duration.ofSeconds(2), 0));
        assertThrows(IllegalArgumentException.class, () -> new NotificationDeliveryRetryPolicy(3, Duration.ofSeconds(2), Duration.ofSeconds(1), 0));
    }
}
