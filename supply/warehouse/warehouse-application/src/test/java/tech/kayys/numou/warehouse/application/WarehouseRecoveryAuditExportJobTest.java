package tech.kayys.numou.warehouse.application;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class WarehouseRecoveryAuditExportJobTest {
    private final Instant created = Instant.parse("2026-10-24T00:00:00Z");
    private final Instant now = created.plusSeconds(10);
    private WarehouseRecoveryAuditExportJob requested() {
        return new WarehouseRecoveryAuditExportJob("exp-1", "tenant-1", "operator-1",
                WarehouseRecoveryAuditExportJob.Status.REQUESTED, null, 0, 0, null, null,
                created, created, null, null, 0);
    }

    @Test void checkpointCanResumeFromDurableCursorAndCounters() {
        var running = requested().claim("worker-a", now, now.plusSeconds(30));
        var checkpoint = running.checkpoint("worker-a", "cursor-2", 50, 4096,
                now.plusSeconds(1), now.plusSeconds(60));
        assertEquals("cursor-2", checkpoint.continuationCursor());
        assertEquals(50, checkpoint.exportedEventCount());
        assertEquals(4096, checkpoint.exportedByteCount());
        assertEquals(running.version() + 1, checkpoint.version());
        assertThrows(IllegalArgumentException.class, () -> checkpoint.checkpoint(
                "worker-a", "cursor-3", 49, 5000, now.plusSeconds(2), now.plusSeconds(90)));
    }

    @Test void liveLeaseCannotBeStolenButExpiredLeaseCanBeReclaimed() {
        var running = requested().claim("worker-a", now, now.plusSeconds(30));
        assertThrows(IllegalStateException.class,
                () -> running.claim("worker-b", now.plusSeconds(1), now.plusSeconds(60)));
        var reclaimed = running.claim("worker-b", now.plusSeconds(31), now.plusSeconds(90));
        assertEquals("worker-b", reclaimed.leaseOwner());
        assertEquals(running.version() + 1, reclaimed.version());
    }

    @Test void completionRequiresVerificationAndArtifactLocation() {
        var running = requested().claim("worker-a", now, now.plusSeconds(30));
        assertThrows(IllegalStateException.class, () -> running.complete(now.plusSeconds(1)));
        var verifying = running.beginVerification("worker-a", now.plusSeconds(1), "object://bucket/export.csv");
        assertEquals(WarehouseRecoveryAuditExportJob.Status.COMPLETED,
                verifying.complete(now.plusSeconds(2)).status());
        assertThrows(IllegalStateException.class, () -> running.beginVerification(
                "worker-a", now.plusSeconds(40), "object://bucket/export.csv"));
    }

    @Test void failedJobUsesSanitizedFailureCode() {
        var failed = requested().fail(now, "ARCHIVE_CHECKSUM_MISMATCH");
        assertEquals(WarehouseRecoveryAuditExportJob.Status.FAILED, failed.status());
        assertThrows(IllegalStateException.class, () -> failed.complete(now.plusSeconds(1)));
    }

    @Test void accessEventRequiresIdentityAndTenant() {
        assertThrows(IllegalArgumentException.class, () -> new WarehouseRecoveryAuditExportAccessEvent(
                "event-1", " ", "exp-1", "operator-1",
                WarehouseRecoveryAuditExportAccessEvent.Action.DOWNLOAD,
                WarehouseRecoveryAuditExportAccessEvent.Outcome.DENIED, "TENANT_SCOPE_MISMATCH", now));
    }
}
