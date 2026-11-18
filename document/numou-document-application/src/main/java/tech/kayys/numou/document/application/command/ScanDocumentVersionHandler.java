package tech.kayys.numou.document.application.command;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.accounting.domain.document.DocumentId;
import tech.kayys.numou.accounting.domain.document.DocumentScanResult;
import tech.kayys.numou.accounting.domain.document.DocumentScanStatus;
import tech.kayys.numou.accounting.domain.document.DocumentVersionId;
import tech.kayys.numou.accounting.domain.document.DocumentVersionScanCompleted;
import tech.kayys.numou.document.application.port.DocumentOutboxPort;
import tech.kayys.numou.document.application.port.DocumentRepository;
import tech.kayys.numou.document.application.port.DocumentScanResultRepository;
import tech.kayys.numou.document.application.port.DocumentScanner;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;

import java.util.List;
import java.util.Objects;

/** Scans and records the exact immutable document version; upload completion is not approval. */
public final class ScanDocumentVersionHandler {
    private final DocumentRepository documents;
    private final DocumentScanner scanner;
    private final DocumentScanResultRepository results;
    private final DocumentOutboxPort outbox;
    private final UnitOfWork unitOfWork;

    public ScanDocumentVersionHandler(
            DocumentRepository documents,
            DocumentScanner scanner,
            DocumentScanResultRepository results,
            DocumentOutboxPort outbox,
            UnitOfWork unitOfWork
    ) {
        this.documents = Objects.requireNonNull(documents);
        this.scanner = Objects.requireNonNull(scanner);
        this.results = Objects.requireNonNull(results);
        this.outbox = Objects.requireNonNull(outbox);
        this.unitOfWork = Objects.requireNonNull(unitOfWork);
    }

    public Uni<DocumentScanResult> handle(ScanDocumentVersion command) {
        Objects.requireNonNull(command, "command");
        var documentId = DocumentId.of(command.documentId());
        var versionId = DocumentVersionId.of(command.documentVersionId());

        return documents.findById(command.tenantId(), documentId)
                .chain(optional -> {
                    if (optional.isEmpty()) {
                        return Uni.createFrom().failure(
                                new IllegalArgumentException("Document was not found for this tenant"));
                    }
                    var document = optional.get();
                    var version = document.versions().stream()
                            .filter(candidate -> candidate.id().equals(versionId))
                            .findFirst()
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "Document version was not found for this tenant and document"));
                    var request = new DocumentScanner.ScanRequest(
                            command.tenantId(), command.documentId(), command.documentVersionId(),
                            version.storageKey(), version.sha256Hash());
                    return scanner.scan(request).chain(verdict -> {
                        var status = switch (verdict.outcome()) {
                            case CLEAN -> DocumentScanStatus.CLEAN;
                            case INFECTED -> DocumentScanStatus.INFECTED;
                            case FAILED -> DocumentScanStatus.FAILED;
                        };
                        var result = new DocumentScanResult(
                                documentId, versionId, status, verdict.scanner(),
                                verdict.signatureVersion(), verdict.failureCode(), verdict.scannedAt());
                        var event = new DocumentVersionScanCompleted(
                                documentId, versionId, status, verdict.scanner(),
                                verdict.signatureVersion(), verdict.failureCode(), verdict.scannedAt());
                        return unitOfWork.execute(() -> results.record(command.tenantId(), result)
                                .chain(recorded -> recorded
                                        ? outbox.append(command.tenantId(), List.of(event))
                                                .replaceWith(result)
                                        : Uni.createFrom().item(result)));
                    });
                });
    }
}
