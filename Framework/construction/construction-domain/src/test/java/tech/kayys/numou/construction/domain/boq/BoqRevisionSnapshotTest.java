package tech.kayys.numou.construction.domain.boq;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BoqRevisionSnapshotTest {
    @Test void approvalReturnsNewSnapshotAndPreservesOriginalDraft() {
        UUID boqId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID creator = UUID.randomUUID();
        UUID reviewer = UUID.randomUUID();
        UUID approver = UUID.randomUUID();
        Instant created = Instant.parse("2026-01-01T00:00:00Z");
        var item = new BoqRevisionSnapshot.ItemSnapshot(UUID.randomUUID(), UUID.randomUUID(),
                "EX-001", "Excavation", BoqItemType.WORK,
                new BigDecimal("2.5"), "m3", new BigDecimal("100.00"), "m3", "IDR",
                new BigDecimal("250.00"));
        var draft = BoqRevisionSnapshot.draft(boqId, projectId, 1, "Main BOQ", List.of(item),
                "boq-calc-v1", creator, created);
        var submitted = draft.submit(reviewer, created.plusSeconds(60));
        var approved = submitted.approve(approver, created.plusSeconds(120));

        assertEquals(BoqRevisionSnapshot.RevisionStatus.DRAFT, draft.status());
        assertEquals(BoqRevisionSnapshot.RevisionStatus.UNDER_REVIEW, submitted.status());
        assertEquals(BoqRevisionSnapshot.RevisionStatus.APPROVED, approved.status());
        assertEquals(new BigDecimal("250.00"), approved.totalAmount());
        assertThrows(IllegalArgumentException.class, () -> submitted.approve(reviewer, created.plusSeconds(121)));
        assertThrows(IllegalStateException.class, () -> approved.submit(reviewer, created.plusSeconds(180)));
        assertThrows(UnsupportedOperationException.class, () -> approved.items().add(item));
    }
}
