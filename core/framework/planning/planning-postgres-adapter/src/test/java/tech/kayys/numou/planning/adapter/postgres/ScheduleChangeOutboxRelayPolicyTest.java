package tech.kayys.numou.planning.adapter.postgres;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScheduleChangeOutboxRelayPolicyTest {
    @Test
    void retryDelayUsesBoundedExponentialBackoff() {
        assertEquals(2, ScheduleChangeOutboxRelay.retryDelaySeconds(1));
        assertEquals(4, ScheduleChangeOutboxRelay.retryDelaySeconds(2));
        assertEquals(8, ScheduleChangeOutboxRelay.retryDelaySeconds(3));
        assertEquals(256, ScheduleChangeOutboxRelay.retryDelaySeconds(8));
        assertEquals(300, ScheduleChangeOutboxRelay.retryDelaySeconds(9));
        assertEquals(300, ScheduleChangeOutboxRelay.retryDelaySeconds(10));
    }

    @Test
    void retryDelayRejectsInvalidAttemptNumbers() {
        assertThrows(IllegalArgumentException.class, () -> ScheduleChangeOutboxRelay.retryDelaySeconds(0));
        assertThrows(IllegalArgumentException.class, () -> ScheduleChangeOutboxRelay.retryDelaySeconds(-1));
    }
}
