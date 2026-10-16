package tech.kayys.syirkah.accounting.domain.document;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class UploadSessionTest {
    private static final Instant NOW = Instant.parse("2026-10-01T00:00:00Z");

    @Test
    void upload_session_is_bound_to_tenant_and_expires_at_boundary() {
        var session = session();

        assertEquals("tenant-a", session.tenantId());
        assertFalse(session.isExpiredAt(NOW.plusSeconds(59)));
        assertTrue(session.isExpiredAt(NOW.plusSeconds(60)));
    }

    @Test
    void completed_session_is_idempotent_for_same_document_version() {
        var session = session();
        var documentId = DocumentId.generate();
        var versionId = DocumentVersionId.generate();

        session.complete(documentId, versionId, NOW.plusSeconds(1));
        session.complete(documentId, versionId, NOW.plusSeconds(2));

        assertEquals(UploadSessionStatus.COMPLETED, session.status());
        assertEquals(documentId, session.completedDocumentId());
        assertEquals(versionId, session.completedVersionId());
        assertThrows(
                IllegalStateException.class,
                () -> session.complete(DocumentId.generate(), DocumentVersionId.generate(), NOW.plusSeconds(3))
        );
    }

    @Test
    void rejects_expired_completion_and_invalid_checksum() {
        var expired = session();
        assertThrows(
                IllegalStateException.class,
                () -> expired.complete(DocumentId.generate(), DocumentVersionId.generate(), NOW.plusSeconds(60))
        );
        assertEquals(UploadSessionStatus.EXPIRED, expired.status());
        assertThrows(
                IllegalArgumentException.class,
                () -> UploadSession.create(
                        UploadSessionId.generate(), "tenant-a", "key", DocumentType.GENERAL,
                        DocumentClassification.INTERNAL, DocumentMetadata.of("a.txt", "text/plain", 1),
                        "bad-digest", NOW.plusSeconds(60)
                )
        );
    }

    private static UploadSession session() {
        return UploadSession.create(
                UploadSessionId.generate(),
                "tenant-a",
                "tenant-a/opaque-key",
                DocumentType.GENERAL,
                DocumentClassification.INTERNAL,
                DocumentMetadata.of("a.txt", "text/plain", 1),
                null,
                NOW.plusSeconds(60)
        );
    }
}
