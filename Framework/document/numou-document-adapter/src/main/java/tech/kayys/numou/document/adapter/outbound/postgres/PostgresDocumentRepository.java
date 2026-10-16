package tech.kayys.syirkah.document.adapter.outbound.postgres;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.syirkah.accounting.domain.document.Document;
import tech.kayys.syirkah.accounting.domain.document.DocumentClassification;
import tech.kayys.syirkah.accounting.domain.document.DocumentId;
import tech.kayys.syirkah.accounting.domain.document.DocumentMetadata;
import tech.kayys.syirkah.accounting.domain.document.DocumentStatus;
import tech.kayys.syirkah.accounting.domain.document.DocumentType;
import tech.kayys.syirkah.accounting.domain.document.DocumentVersion;
import tech.kayys.syirkah.accounting.domain.document.DocumentVersionId;
import tech.kayys.syirkah.document.application.port.DocumentRepository;

import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PostgresDocumentRepository implements DocumentRepository {
    @Override
    public Uni<Optional<Document>> findById(String tenantId, DocumentId documentId) {
        var tenantUuid = parseUuid(tenantId, "tenantId");
        var documentUuid = parseUuid(documentId.value(), "documentId");
        return DocumentEntity.<DocumentEntity>find(
                        "tenantId = ?1 and id = ?2",
                        tenantUuid,
                        documentUuid
                )
                .firstResult()
                .chain(entity -> {
                    if (entity == null) {
                        return Uni.createFrom().item(Optional.empty());
                    }
                    return DocumentVersionEntity.<DocumentVersionEntity>find(
                                    "tenantId = ?1 and documentId = ?2",
                                    tenantUuid,
                                    documentUuid
                            )
                            .list()
                            .map(versions -> Optional.of(toDomain(entity, versions)));
                });
    }

    @Override
    public Uni<Void> save(String tenantId, Document document) {
        var tenantUuid = parseUuid(tenantId, "tenantId");
        var documentUuid = parseUuid(document.id().value(), "documentId");
        return DocumentEntity.<DocumentEntity>find(
                        "tenantId = ?1 and id = ?2",
                        tenantUuid,
                        documentUuid
                )
                .firstResult()
                .chain(entity -> {
                    var isNew = entity == null;
                    var target = isNew ? new DocumentEntity() : entity;
                    target.id = documentUuid;
                    target.tenantId = tenantUuid;
                    target.documentType = document.type().name();
                    target.classification = document.classification().name();
                    target.status = document.status().name();
                    target.filename = document.metadata().filename();
                    target.contentType = document.metadata().contentType();
                    target.fileSize = document.metadata().fileSize();
                    target.customAttributes = document.metadata().customAttributes();
                    var persist = isNew
                            ? Panache.getSession().chain(session -> session.persist(target))
                            : Uni.createFrom().voidItem();
                    return persist.chain(() -> persistVersions(tenantUuid, documentUuid, document));
                });
    }

    private Uni<Void> persistVersions(UUID tenantId, UUID documentId, Document document) {
        return DocumentVersionEntity.<DocumentVersionEntity>find(
                        "tenantId = ?1 and documentId = ?2",
                        tenantId,
                        documentId
                )
                .list()
                .chain(existing -> {
                    var existingIds = new HashSet<UUID>();
                    existing.forEach(version -> existingIds.add(version.id));
                    Uni<Void> writes = Uni.createFrom().voidItem();
                    for (var version : document.versions()) {
                        var versionId = parseUuid(version.id().value(), "documentVersionId");
                        if (!existingIds.contains(versionId)) {
                            var entity = new DocumentVersionEntity();
                            entity.id = versionId;
                            entity.tenantId = tenantId;
                            entity.documentId = documentId;
                            entity.versionNumber = version.versionNumber();
                            entity.sha256Hash = version.sha256Hash();
                            entity.storageKey = version.storageKey();
                            entity.createdAt = version.createdAt();
                            writes = writes.chain(() ->
                                    Panache.getSession().chain(session -> session.persist(entity))
                            );
                        }
                    }
                    return writes;
                });
    }

    private static Document toDomain(DocumentEntity entity, java.util.List<DocumentVersionEntity> versionEntities) {
        var versions = versionEntities.stream()
                .map(version -> new DocumentVersion(
                        DocumentVersionId.of(version.id.toString()),
                        version.versionNumber,
                        version.sha256Hash,
                        version.storageKey,
                        version.createdAt
                ))
                .toList();
        return Document.reconstitute(
                DocumentId.of(entity.id.toString()),
                DocumentType.valueOf(entity.documentType),
                DocumentClassification.valueOf(entity.classification),
                DocumentStatus.valueOf(entity.status),
                new DocumentMetadata(
                        entity.filename, entity.contentType, entity.fileSize, entity.customAttributes
                ),
                versions
        );
    }

    static UUID parseUuid(String value, String field) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException(field + " must be a UUID", invalid);
        }
    }
}
