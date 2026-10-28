package tech.kayys.numou.construction.domain.consultancy;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ConsultancyDeliverableHistoryEntryTest {
    @Test
    void rejectedSnapshotRequiresReasonAndReviewer() {
        assertThrows(IllegalArgumentException.class, () -> new ConsultancyDeliverableHistoryEntry(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1,
                ConsultancyDeliverableStatus.REJECTED, ConsultancyDeliverableHistoryEntry.Action.REJECTED,
                "Report", "Description", UUID.randomUUID(), " ", UUID.randomUUID(), Instant.now()));
    }

    @Test
    void preservesSnapshotFieldsAsAnImmutableRecord() {
        UUID historyId = UUID.randomUUID();
        UUID deliverableId = UUID.randomUUID();
        ConsultancyDeliverableHistoryEntry entry = new ConsultancyDeliverableHistoryEntry(
                historyId, deliverableId, UUID.randomUUID(), 2,
                ConsultancyDeliverableStatus.DRAFT, ConsultancyDeliverableHistoryEntry.Action.REVISED,
                "Revision 2", "Updated scope", null, null, UUID.randomUUID(), Instant.parse("2026-10-28T10:00:00Z"));
        assertEquals(historyId, entry.historyId());
        assertEquals(deliverableId, entry.deliverableId());
        assertEquals(2, entry.revision());
        assertEquals("Revision 2", entry.title());
    }
}
