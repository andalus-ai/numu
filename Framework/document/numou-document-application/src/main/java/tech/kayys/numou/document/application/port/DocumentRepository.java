package tech.kayys.numou.document.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.accounting.domain.document.Document;
import tech.kayys.numou.accounting.domain.document.DocumentId;

import java.util.Optional;

public interface DocumentRepository {
    Uni<Optional<Document>> findById(String tenantId, DocumentId documentId);

    Uni<Void> save(String tenantId, Document document);
}
