package tech.kayys.numou.accounting.domain.document;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class DocumentVersionScanCompletedTest {
    @Test
    void completionEventIsVersionScopedAndCannotRepresentPending() {
        var documentId = DocumentId.generate();
        var versionId = DocumentVersionId.generate();
        var occurredAt = Instant.parse("2026-11-18T01:00:00Z");

        var event = new DocumentVersionScanCompleted(
                documentId, versionId, DocumentScanStatus.CLEAN,
                "clamav", "db-2026-11-18", null, occurredAt);

        assertEquals("document.version-scan-completed", event.eventType());
        assertNotNull(event.eventId());
        assertThrows(IllegalArgumentException.class, () -> new DocumentVersionScanCompleted(
                documentId, versionId, DocumentScanStatus.PENDING,
                "clamav", "db-2026-11-18", null, occurredAt));
    }

    @Test
    void failedEventRequiresFailureCode() {
        assertThrows(IllegalArgumentException.class, () -> new DocumentVersionScanCompleted(
                DocumentId.generate(), DocumentVersionId.generate(), DocumentScanStatus.FAILED,
                "clamav", "db-2026-11-18", null, Instant.now()));
    }
}
