package tech.kayys.numou.document.adapter.outbound.postgres;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.numou.accounting.domain.document.DocumentId;
import tech.kayys.numou.accounting.domain.document.DocumentLink;
import tech.kayys.numou.accounting.domain.document.DocumentLinkId;
import tech.kayys.numou.accounting.domain.document.DocumentVersionId;
import tech.kayys.numou.document.application.port.DocumentLinkRepository;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class PostgresDocumentLinkRepository implements DocumentLinkRepository {
    @Override
    public Uni<Void> save(String tenantId, DocumentLink link) {
        UUID tenantUuid = PostgresDocumentRepository.parseUuid(tenantId, "tenantId");
        DocumentLinkEntity entity = new DocumentLinkEntity();
        entity.id = PostgresDocumentRepository.parseUuid(link.id().value(), "documentLinkId");
        entity.tenantId = tenantUuid;
        entity.documentId = PostgresDocumentRepository.parseUuid(link.documentId().value(), "documentId");
        entity.documentVersionId = link.documentVersionId() == null
                ? null
                : PostgresDocumentRepository.parseUuid(link.documentVersionId().value(), "documentVersionId");
        entity.aggregateType = requireText(link.aggregateType(), "aggregateType");
        entity.aggregateId = requireText(link.aggregateId(), "aggregateId");
        entity.relationship = requireText(link.relationship(), "relationship");
        entity.linkedAt = link.linkedAt();

        // The migration's composite foreign keys enforce tenant and document/version ownership.
        return Panache.getSession().chain(session -> session.persist(entity));
    }

    @Override
    public Uni<List<DocumentLink>> findByAggregate(
            String tenantId, String aggregateType, String aggregateId) {
        UUID tenantUuid = PostgresDocumentRepository.parseUuid(tenantId, "tenantId");
        String type = requireText(aggregateType, "aggregateType");
        String id = requireText(aggregateId, "aggregateId");
        return DocumentLinkEntity.<DocumentLinkEntity>find(
                        "tenantId = ?1 and aggregateType = ?2 and aggregateId = ?3 order by linkedAt asc, id asc",
                        tenantUuid, type, id)
                .list()
                .map(rows -> rows.stream().map(PostgresDocumentLinkRepository::toDomain).toList());
    }

    private static DocumentLink toDomain(DocumentLinkEntity entity) {
        return new DocumentLink(
                DocumentLinkId.of(entity.id.toString()),
                DocumentId.of(entity.documentId.toString()),
                entity.documentVersionId == null ? null : DocumentVersionId.of(entity.documentVersionId.toString()),
                entity.aggregateType,
                entity.aggregateId,
                entity.relationship,
                entity.linkedAt);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
