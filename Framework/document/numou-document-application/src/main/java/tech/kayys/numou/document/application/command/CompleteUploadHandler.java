package tech.kayys.syirkah.document.application.command;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.domain.document.Document;
import tech.kayys.syirkah.accounting.domain.document.DocumentId;
import tech.kayys.syirkah.accounting.domain.document.UploadSessionId;
import tech.kayys.syirkah.document.application.port.DocumentAccessPort;
import tech.kayys.syirkah.document.application.port.DocumentOutboxPort;
import tech.kayys.syirkah.document.application.port.DocumentRepository;
import tech.kayys.syirkah.document.application.port.DocumentStoragePort;
import tech.kayys.syirkah.document.application.port.UploadSessionRepository;
import tech.kayys.syirkah.foundation.application.transaction.UnitOfWork;
import tech.kayys.syirkah.foundation.domain.time.DomainClock;

import java.util.Objects;

public final class CompleteUploadHandler {
    private final DocumentAccessPort access;
    private final DocumentStoragePort storage;
    private final DocumentRepository documents;
    private final UploadSessionRepository sessions;
    private final DocumentOutboxPort outbox;
    private final UnitOfWork unitOfWork;
    private final DomainClock clock;

    public CompleteUploadHandler(
            DocumentAccessPort access,
            DocumentStoragePort storage,
            DocumentRepository documents,
            UploadSessionRepository sessions,
            DocumentOutboxPort outbox,
            UnitOfWork unitOfWork,
            DomainClock clock
    ) {
        this.access = Objects.requireNonNull(access);
        this.storage = Objects.requireNonNull(storage);
        this.documents = Objects.requireNonNull(documents);
        this.sessions = Objects.requireNonNull(sessions);
        this.outbox = Objects.requireNonNull(outbox);
        this.unitOfWork = Objects.requireNonNull(unitOfWork);
        this.clock = Objects.requireNonNull(clock);
    }

    public Uni<CompleteUploadResult> handle(CompleteUpload command) {
        Objects.requireNonNull(command, "command cannot be null");
        if (command.tenantId() == null || command.tenantId().isBlank()) {
            return Uni.createFrom().failure(new IllegalArgumentException("tenantId cannot be blank"));
        }
        Objects.requireNonNull(command.uploadSessionId(), "uploadSessionId cannot be null");

        return access.require(command.tenantId(), "document.create")
                .chain(() -> sessions.findById(command.tenantId(), command.uploadSessionId()))
                .chain(optional -> {
                    if (optional.isEmpty()) {
                        return Uni.createFrom().failure(
                                new IllegalArgumentException("Upload session not found")
                        );
                    }
                    var session = optional.get();
                    if (session.status() == tech.kayys.syirkah.accounting.domain.document.UploadSessionStatus.COMPLETED) {
                        return Uni.createFrom().item(resultFor(session));
                    }
                    var now = clock.now();
                    if (session.isExpiredAt(now)) {
                        return Uni.createFrom().failure(new IllegalStateException("Upload session has expired"));
                    }
                    return storage.inspect(session.storageKey()).chain(stored -> {
                        validateStoredObject(session.expectedSize(), session.expectedSha256(),
                                session.metadata().contentType(), stored);
                        return unitOfWork.execute(() ->
                                sessions.findByIdForUpdate(command.tenantId(), command.uploadSessionId())
                                        .chain(reloaded -> {
                                            if (reloaded.isEmpty()) {
                                                return Uni.createFrom().failure(
                                                        new IllegalArgumentException("Upload session not found")
                                                );
                                            }
                                            var current = reloaded.get();
                                            if (current.status()
                                                    == tech.kayys.syirkah.accounting.domain.document.UploadSessionStatus.COMPLETED) {
                                                return Uni.createFrom().item(resultFor(current));
                                            }
                                            if (current.isExpiredAt(clock.now())) {
                                                return Uni.createFrom().failure(
                                                        new IllegalStateException("Upload session has expired")
                                                );
                                            }
                                            var document = new Document(
                                                    DocumentId.generate(),
                                                    current.documentType(),
                                                    current.classification(),
                                                    current.metadata(),
                                                    stored.sha256(),
                                                    current.storageKey(),
                                                    clock.now()
                                            );
                                            var version = document.latestVersion();
                                            current.complete(document.id(), version.id(), clock.now());
                                            return documents.save(current.tenantId(), document)
                                                    .chain(() -> sessions.save(current))
                                                    .chain(() -> outbox.append(
                                                            current.tenantId(),
                                                            document.pullDomainEvents()
                                                    ))
                                                    .replaceWith(new CompleteUploadResult(
                                                            document.id(), version.id(), version.versionNumber()
                                                    ));
                                        })
                        );
                    });
                });
    }

    private static CompleteUploadResult resultFor(
            tech.kayys.syirkah.accounting.domain.document.UploadSession session
    ) {
        return new CompleteUploadResult(
                session.completedDocumentId(),
                session.completedVersionId(),
                1
        );
    }

    private static void validateStoredObject(
            long expectedSize,
            String expectedSha256,
            String expectedContentType,
            DocumentStoragePort.StoredDocument stored
    ) {
        if (stored.size() != expectedSize) {
            throw new IllegalStateException("Uploaded object size does not match the upload request");
        }
        if (stored.contentType() == null || !stored.contentType().equalsIgnoreCase(expectedContentType)) {
            throw new IllegalStateException("Uploaded object content type does not match the upload request");
        }
        if (stored.sha256() == null || !stored.sha256().matches("(?i)[0-9a-f]{64}")) {
            throw new IllegalStateException("Storage did not provide a valid SHA-256 checksum");
        }
        if (expectedSha256 != null && !expectedSha256.equalsIgnoreCase(stored.sha256())) {
            throw new IllegalStateException("Uploaded object checksum does not match the upload request");
        }
    }
}
