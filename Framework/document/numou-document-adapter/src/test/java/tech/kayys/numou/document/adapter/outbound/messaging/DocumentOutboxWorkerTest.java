package tech.kayys.syirkah.document.adapter.outbound.messaging;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DocumentOutboxWorkerTest {
    @Test
    void retry_delay_is_exponential_and_capped() {
        assertEquals(Duration.ofSeconds(5), DocumentOutboxWorker.retryDelay(1));
        assertEquals(Duration.ofSeconds(15), DocumentOutboxWorker.retryDelay(2));
        assertEquals(Duration.ofSeconds(45), DocumentOutboxWorker.retryDelay(3));
        assertEquals(Duration.ofSeconds(135), DocumentOutboxWorker.retryDelay(4));
        assertEquals(Duration.ofSeconds(300), DocumentOutboxWorker.retryDelay(5));
        assertEquals(Duration.ofSeconds(300), DocumentOutboxWorker.retryDelay(50));
    }

    @Test
    void retry_delay_requires_a_positive_attempt_number() {
        assertThrows(IllegalArgumentException.class, () -> DocumentOutboxWorker.retryDelay(0));
    }
}
