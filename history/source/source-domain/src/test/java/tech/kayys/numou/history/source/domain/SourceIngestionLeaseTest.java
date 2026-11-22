package tech.kayys.numou.history.source.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SourceIngestionLeaseTest {
    private static final Instant NOW = Instant.parse("2026-11-20T00:00:00Z");

    @Test
    void leaseRequiresIdentityWorkerPositiveAttemptAndForwardExpiry() {
        var lease = new SourceIngestionLease(UUID.randomUUID(), "worker:ocr", UUID.randomUUID(),
                1, NOW, NOW.plusSeconds(30));
        assertEquals(1, lease.attempt());
        assertThrows(IllegalArgumentException.class, () -> new SourceIngestionLease(
                lease.ingestionId(), "worker:ocr", lease.leaseToken(), 0, NOW, NOW.plusSeconds(30)));
        assertThrows(IllegalArgumentException.class, () -> new SourceIngestionLease(
                lease.ingestionId(), "worker:ocr", lease.leaseToken(), 1, NOW, NOW));
        assertThrows(IllegalArgumentException.class, () -> new SourceIngestionLease(
                lease.ingestionId(), " ", lease.leaseToken(), 1, NOW, NOW.plusSeconds(30)));
    }
}
