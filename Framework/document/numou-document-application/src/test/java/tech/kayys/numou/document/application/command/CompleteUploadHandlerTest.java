package tech.kayys.syirkah.document.application.command;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.accounting.domain.document.Document;
import tech.kayys.syirkah.accounting.domain.document.DocumentClassification;
import tech.kayys.syirkah.accounting.domain.document.DocumentId;
import tech.kayys.syirkah.accounting.domain.document.DocumentMetadata;
import tech.kayys.syirkah.accounting.domain.document.DocumentType;
import tech.kayys.syirkah.accounting.domain.document.UploadSession;
import tech.kayys.syirkah.accounting.domain.document.UploadSessionId;
import tech.kayys.syirkah.document.application.port.DocumentAccessPort;
import tech.kayys.syirkah.document.application.port.DocumentOutboxPort;
import tech.kayys.syirkah.document.application.port.DocumentRepository;
import tech.kayys.syirkah.document.application.port.DocumentStoragePort;
import tech.kayys.syirkah.document.application.port.UploadSessionRepository;
import tech.kayys.syirkah.foundation.application.transaction.UnitOfWork;
import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import tech.kayys.syirkah.foundation.domain.time.DomainClock;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CompleteUploadHandlerTest {
    private static final Instant NOW = Instant.parse("2026-10-01T00:00:00Z");
    private static final String SHA256 = "a".repeat(64);

    @Test
    void verifies_binary_persists_once_and_returns_same_result_on_retry() {
        var session = session();
        var fixture = new Fixture(session, new DocumentStoragePort.StoredDocument(512, "application/pdf", SHA256));
        var command = new CompleteUpload("tenant-a", session.id());

        var first = fixture.handler.handle(command).await().indefinitely();
        var second = fixture.handler.handle(command).await().indefinitely();

        assertEquals(first, second);
        assertEquals(1, fixture.documents.size());
        assertEquals(1, fixture.outboxEvents.size());
        assertEquals(1, first.versionNumber());
    }

    @Test
    void checksum_or_size_mismatch_fails_before_any_database_writes() {
        var session = session();
        var fixture = new Fixture(session, new DocumentStoragePort.StoredDocument(511, "application/pdf", SHA256));

        assertThrows(
                RuntimeException.class,
                () -> fixture.handler.handle(new CompleteUpload("tenant-a", session.id()))
                        .await().indefinitely()
        );

        assertTrue(fixture.documents.isEmpty());
        assertTrue(fixture.outboxEvents.isEmpty());
        assertEquals(
                tech.kayys.syirkah.accounting.domain.document.UploadSessionStatus.PENDING,
                session.status()
        );
    }

    private static UploadSession session() {
        return UploadSession.create(
                UploadSessionId.generate(), "tenant-a", "tenant-a/object-key",
                DocumentType.CONTRACT, DocumentClassification.CONFIDENTIAL,
                DocumentMetadata.of("contract.pdf", "application/pdf", 512),
                SHA256, NOW.plusSeconds(600)
        );
    }

    private static final class Fixture {
        private final Map<DocumentId, Document> documents = new HashMap<>();
        private final List<DomainEvent> outboxEvents = new java.util.ArrayList<>();
        private final UploadSession session;
        private final CompleteUploadHandler handler;

        private Fixture(UploadSession session, DocumentStoragePort.StoredDocument stored) {
            this.session = session;
            DocumentAccessPort access = (tenantId, permission) -> Uni.createFrom().voidItem();
            DocumentStoragePort storage = new DocumentStoragePort() {
                @Override
                public Uni<UploadTarget> createUploadTarget(UploadTargetRequest request) {
                    return Uni.createFrom().failure(new UnsupportedOperationException());
                }

                @Override
                public Uni<StoredDocument> inspect(String storageKey) {
                    return Uni.createFrom().item(stored);
                }

                @Override
                public Uni<Void> delete(String storageKey) {
                    return Uni.createFrom().voidItem();
                }
            };
            DocumentRepository documentRepository = new DocumentRepository() {
                @Override
                public Uni<Optional<Document>> findById(String tenantId, DocumentId documentId) {
                    return Uni.createFrom().item(Optional.ofNullable(documents.get(documentId)));
                }

                @Override
                public Uni<Void> save(String tenantId, Document document) {
                    documents.put(document.id(), document);
                    return Uni.createFrom().voidItem();
                }
            };
            UploadSessionRepository sessions = new UploadSessionRepository() {
                @Override
                public Uni<Optional<UploadSession>> findById(String tenantId, UploadSessionId uploadSessionId) {
                    return Uni.createFrom().item(
                            tenantId.equals(Fixture.this.session.tenantId())
                                    && uploadSessionId.equals(Fixture.this.session.id())
                                    ? Optional.of(Fixture.this.session)
                                    : Optional.empty()
                    );
                }

                @Override
                public Uni<Void> save(UploadSession uploadSession) {
                    assertSame(Fixture.this.session, uploadSession);
                    return Uni.createFrom().voidItem();
                }
            };
            DocumentOutboxPort outbox = (tenantId, events) -> {
                outboxEvents.addAll(events);
                return Uni.createFrom().voidItem();
            };
            UnitOfWork unitOfWork = new UnitOfWork() {
                @Override
                public <R> Uni<R> execute(java.util.function.Supplier<Uni<R>> work) {
                    return work.get();
                }
            };
            DomainClock clock = () -> NOW.plusSeconds(1);
            handler = new CompleteUploadHandler(
                    access, storage, documentRepository, sessions, outbox, unitOfWork, clock
            );
        }
    }
}
