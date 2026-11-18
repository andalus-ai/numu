package tech.kayys.numou.accounting.domain.document;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class DocumentScanResultTest {
    private static final DocumentId DOCUMENT_ID = DocumentId.generate();
    private static final DocumentVersionId VERSION_ID = DocumentVersionId.generate();
    private static final Instant NOW = Instant.parse("2026-11-17T00:00:00Z");

    @Test
    void onlyCleanVerdictIsSafeToServe() {
        assertTrue(result(DocumentScanStatus.CLEAN, null).isSafeToServe());
        assertFalse(result(DocumentScanStatus.INFECTED, null).isSafeToServe());
        assertFalse(result(DocumentScanStatus.FAILED, "SCANNER_TIMEOUT").isSafeToServe());
    }

    @Test
    void failedVerdictRequiresFailureCode() {
        assertThrows(IllegalArgumentException.class,
                () -> result(DocumentScanStatus.FAILED, null));
    }

    @Test
    void pendingIsNotAPersistedVerdict() {
        assertThrows(IllegalArgumentException.class,
                () -> result(DocumentScanStatus.PENDING, null));
    }

    @Test
    void cleanVerdictCannotCarryFailureCode() {
        assertThrows(IllegalArgumentException.class,
                () -> result(DocumentScanStatus.CLEAN, "MALFORMED"));
    }

    private static DocumentScanResult result(DocumentScanStatus status, String failureCode) {
        return new DocumentScanResult(
                DOCUMENT_ID, VERSION_ID, status, "test-scanner", "sig-2026.11",
                failureCode, NOW
        );
    }
}
