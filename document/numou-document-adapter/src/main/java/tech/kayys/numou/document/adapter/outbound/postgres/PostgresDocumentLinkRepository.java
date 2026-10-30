package tech.kayys.numou.document.adapter.outbound.postgres;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.accounting.domain.document.DocumentId;
import tech.kayys.numou.accounting.domain.document.DocumentLink;
import tech.kayys.numou.accounting.domain.document.DocumentLinkId;
import tech.kayys.numou.accounting.domain.document.DocumentVersionId;
import tech.kayys.numou.document.application.port.DocumentLinkRepository;

import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class PostgresDocumentLinkRepository implements DocumentLinkRepository {
    private static final String INSERT_LEGACY = """
            INSERT INTO document_links
                (id, tenant_id, document_id, document_version_id, aggregate_type, aggregate_id, relationship, linked_by, linked_at)
            VALUES ($1, $2, $3, NULL, $4, $5, $6, $7, $8)
            ON CONFLICT (tenant_id, document_id, aggregate_type, aggregate_id, relationship)
                WHERE document_version_id IS NULL
            DO NOTHING
            """;

    private static final String INSERT_VERSIONED = """
            INSERT INTO document_links
                (id, tenant_id, document_id, document_version_id, aggregate_type, aggregate_id, relationship, linked_by, linked_at)
            VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
            ON CONFLICT (tenant_id, document_id, document_version_id, aggregate_type, aggregate_id, relationship)
                WHERE document_version_id IS NOT NULL
            DO NOTHING
            """;

    @Inject
    PgPool client;

    @Override
    public Uni<Void> save(String tenantId, DocumentLink link) {
        UUID tenantUuid = PostgresDocumentRepository.parseUuid(tenantId, "tenantId");
        UUID linkUuid = PostgresDocumentRepository.parseUuid(link.id().value(), "documentLinkId");
        UUID documentUuid = PostgresDocumentRepository.parseUuid(link.documentId().value(), "documentId");
        String aggregateType = requireText(link.aggregateType(), "aggregateType");
        String aggregateId = requireText(link.aggregateId(), "aggregateId");
        String relationship = requireText(link.relationship(), "relationship");
        var linkedAt = link.linkedAt().atOffset(ZoneOffset.UTC);

        if (link.documentVersionId() == null) {
            return client.preparedQuery(INSERT_LEGACY)
                    .execute(Tuple.of(linkUuid, tenantUuid, documentUuid,
                            aggregateType, aggregateId, relationship, link.linkedBy(), linkedAt))
                    .replaceWithVoid();
        }

        UUID versionUuid = PostgresDocumentRepository.parseUuid(
                link.documentVersionId().value(), "documentVersionId");
        return client.preparedQuery(INSERT_VERSIONED)
                .execute(Tuple.of(linkUuid, tenantUuid, documentUuid, versionUuid,
                        aggregateType, aggregateId, relationship, link.linkedBy(), linkedAt))
                .replaceWithVoid();
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
                entity.linkedBy,
                entity.linkedAt);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
