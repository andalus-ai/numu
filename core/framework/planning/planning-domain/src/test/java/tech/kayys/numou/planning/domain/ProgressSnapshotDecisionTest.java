package tech.kayys.numou.planning.domain;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ProgressSnapshotDecisionTest {
    @Test
    void requiresReasonAndPreservesTrimmedAuditReason() {
        ProgressSnapshotDecision decision = new ProgressSnapshotDecision(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), ProgressSnapshotDecision.Outcome.APPROVED,
                "  checked against source records  ", Instant.parse("2026-10-23T10:00:00Z"));
        assertEquals("checked against source records", decision.reason());
        assertThrows(IllegalArgumentException.class, () -> new ProgressSnapshotDecision(UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                ProgressSnapshotDecision.Outcome.REJECTED, "  ", Instant.now()));
    }
}
