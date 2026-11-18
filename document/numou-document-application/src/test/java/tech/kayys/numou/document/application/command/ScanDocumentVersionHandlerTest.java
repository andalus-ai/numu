package tech.kayys.numou.document.application.command;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.accounting.domain.document.Document;
import tech.kayys.numou.accounting.domain.document.DocumentClassification;
import tech.kayys.numou.accounting.domain.document.DocumentId;
import tech.kayys.numou.accounting.domain.document.DocumentMetadata;
import tech.kayys.numou.accounting.domain.document.DocumentScanResult;
import tech.kayys.numou.accounting.domain.document.DocumentScanStatus;
import tech.kayys.numou.accounting.domain.document.DocumentType;
import tech.kayys.numou.accounting.domain.document.DocumentVersionId;
import tech.kayys.numou.document.application.port.DocumentOutboxPort;
import tech.kayys.numou.document.application.port.DocumentRepository;
import tech.kayys.numou.document.application.port.DocumentScanResultRepository;
import tech.kayys.numou.document.application.port.DocumentScanner;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ScanDocumentVersionHandlerTest {
    private static final Instant NOW = Instant.parse("2026-11-18T00:00:00Z");
    private static final String HASH = "b".repeat(64);

    @Test
    void scans_exact_immutable_version_and_publishes_clean_result_once() {
        var document = document();
        var fixture = new Fixture(document, verdict(DocumentScanner.Outcome.CLEAN, null));
        var command = fixture.command(document);

        var result = fixture.handler.handle(command).await().indefinitely();

        assertEquals(DocumentScanStatus.CLEAN, result.status());
        assertEquals(document.id().value(), fixture.scanRequest.documentId());
        assertEquals(document.latestVersion().id().value(), fixture.scanRequest.documentVersionId());
        assertEquals(document.latestVersion().storageKey(), fixture.scanRequest.storageKey());
        assertEquals(HASH, fixture.scanRequest.sha256());
        assertEquals(1, fixture.recorded.size());
        assertEquals("document.version-scan-completed", fixture.events.get(0).eventType());
    }

    @Test
    void infected_and_failed_verdicts_are_never_safe_to_serve() {
        var infected = document();
        var infectedFixture = new Fixture(infected, verdict(DocumentScanner.Outcome.INFECTED, null));
        assertFalse(infectedFixture.handler.handle(infectedFixture.command(infected))
                .await().indefinitely().isSafeToServe());

        var failed = document();
        var failedFixture = new Fixture(failed, verdict(DocumentScanner.Outcome.FAILED, "SCANNER_TIMEOUT"));
        assertFalse(failedFixture.handler.handle(failedFixture.command(failed))
                .await().indefinitely().isSafeToServe());
    }

    @Test
    void missing_document_or_version_fails_closed_before_scanner_invocation() {
        var document = document();
        var fixture = new Fixture(document, verdict(DocumentScanner.Outcome.CLEAN, null));

        assertThrows(RuntimeException.class, () -> fixture.handler.handle(
                new ScanDocumentVersion("tenant-a", DocumentId.generate().value(),
                        document.latestVersion().id().value())).await().indefinitely());
        assertThrows(RuntimeException.class, () -> fixture.handler.handle(
                new ScanDocumentVersion("tenant-a", document.id().value(),
                        DocumentVersionId.generate().value())).await().indefinitely());
        assertNull(fixture.scanRequest);
        assertTrue(fixture.recorded.isEmpty());
    }

    @Test
    void idempotent_result_replay_does_not_emit_duplicate_event() {
        var document = document();
        var fixture = new Fixture(document, verdict(DocumentScanner.Outcome.CLEAN, null));
        fixture.recordResult = false;

        fixture.handler.handle(fixture.command(document)).await().indefinitely();

        assertTrue(fixture.events.isEmpty());
    }

    private static Document document() {
        return new Document(DocumentId.generate(), DocumentType.CONTRACT,
                DocumentClassification.CONFIDENTIAL,
                DocumentMetadata.of("evidence.pdf", "application/pdf", 128),
                HASH, "tenant-a/evidence/immutable-object", NOW);
    }

    private static DocumentScanner.ScanVerdict verdict(DocumentScanner.Outcome outcome, String failureCode) {
        return new DocumentScanner.ScanVerdict(outcome, "test-scanner", "signatures-2026-11",
                failureCode, NOW.plusSeconds(1));
    }

    private static final class Fixture {
        private final List<DocumentScanResult> recorded = new ArrayList<>();
        private final List<DomainEvent> events = new ArrayList<>();
        private final ScanDocumentVersionHandler handler;
        private DocumentScanner.ScanRequest scanRequest;
        private boolean recordResult = true;

        private Fixture(Document document, DocumentScanner.ScanVerdict verdict) {
            Map<DocumentId, Document> documents = Map.of(document.id(), document);
            DocumentRepository repository = new DocumentRepository() {
                @Override
                public Uni<Optional<Document>> findById(String tenantId, DocumentId documentId) {
                    return Uni.createFrom().item("tenant-a".equals(tenantId)
                            ? Optional.ofNullable(documents.get(documentId)) : Optional.empty());
                }

                @Override
                public Uni<Void> save(String tenantId, Document value) {
                    return Uni.createFrom().voidItem();
                }
            };
            DocumentScanner scanner = request -> {
                scanRequest = request;
                return Uni.createFrom().item(verdict);
            };
            DocumentScanResultRepository resultRepository = (tenantId, result) -> {
                recorded.add(result);
                return Uni.createFrom().item(recordResult);
            };
            DocumentOutboxPort outbox = (tenantId, domainEvents) -> {
                events.addAll(domainEvents);
                return Uni.createFrom().voidItem();
            };
            UnitOfWork unitOfWork = new UnitOfWork() {
                @Override
                public <R> Uni<R> execute(java.util.function.Supplier<Uni<R>> work) {
                    return work.get();
                }
            };
            handler = new ScanDocumentVersionHandler(repository, scanner, resultRepository, outbox, unitOfWork);
        }

        private ScanDocumentVersion command(Document document) {
            return new ScanDocumentVersion("tenant-a", document.id().value(),
                    document.latestVersion().id().value());
        }
    }
}
