package tech.kayys.numou.history.source.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SourceIngestionDomainTest {
    private static final Instant NOW = Instant.parse("2026-11-18T00:00:00Z");
    private static final String HASH = "A".repeat(64);

    @Test
    void documentRequestRequiresImmutableDocumentVersionAndNormalizesChecksum() {
        var request = SourceIngestionRequest.fromDocument(
                HistoricalSourceId.generate(), UUID.randomUUID(), UUID.randomUUID(),
                HASH, "application/pdf", "actor:researcher", NOW);

        assertEquals(SourceIngestionInputKind.DOCUMENT_VERSION, request.inputKind());
        assertEquals("a".repeat(64), request.inputSha256());
        assertNull(request.inputUri());
        assertThrows(IllegalArgumentException.class, () -> SourceIngestionRequest.fromDocument(
                request.sourceId(), UUID.randomUUID(), UUID.randomUUID(),
                "not-a-checksum", "application/pdf", "actor:researcher", NOW));
    }

    @Test
    void uriRequestSnapshotsOnlyHttpUrisWithoutEmbeddedCredentials() {
        var request = SourceIngestionRequest.fromCanonicalUri(
                HistoricalSourceId.generate(), "https://archive.example/item/7",
                "actor:researcher", NOW);

        assertEquals("https://archive.example/item/7", request.inputUri());
        assertNull(request.inputDocumentId());
        assertThrows(IllegalArgumentException.class, () -> SourceIngestionRequest.fromCanonicalUri(
                request.sourceId(), "file:///etc/passwd", "actor:researcher", NOW));
        assertThrows(IllegalArgumentException.class, () -> SourceIngestionRequest.fromCanonicalUri(
                request.sourceId(), "https://user:secret@archive.example/item/7", "actor:researcher", NOW));
    }

    @Test
    void extractionArtifactRequiresChecksumsAndVersionedDerivedDocumentReference() {
        var artifact = SourceExtractionArtifact.create(
                UUID.randomUUID(), SourceExtractionKind.OCR_TEXT, "tesseract", "5.4",
                UUID.randomUUID(), UUID.randomUUID(), HASH, "b".repeat(64),
                "id-id", "page:4", "worker:ocr-1", NOW);

        assertEquals("id-ID", artifact.languageTag());
        assertEquals("a".repeat(64), artifact.inputSha256());
        assertThrows(IllegalArgumentException.class, () -> SourceExtractionArtifact.create(
                artifact.ingestionId(), artifact.kind(), artifact.extractorId(), artifact.extractorVersion(),
                artifact.outputDocumentId(), artifact.outputDocumentVersionId(),
                "bad", "b".repeat(64), null, null, artifact.workerRef(), NOW));
    }
}
