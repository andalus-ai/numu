package tech.kayys.syirkah.document.adapter.outbound.postgres;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import tech.kayys.syirkah.accounting.domain.document.DocumentClassification;
import tech.kayys.syirkah.accounting.domain.document.DocumentId;
import tech.kayys.syirkah.accounting.domain.document.DocumentMetadata;
import tech.kayys.syirkah.accounting.domain.document.DocumentType;
import tech.kayys.syirkah.accounting.domain.document.DocumentVersionId;
import tech.kayys.syirkah.accounting.domain.document.UploadSession;
import tech.kayys.syirkah.accounting.domain.document.UploadSessionId;
import tech.kayys.syirkah.accounting.domain.document.UploadSessionStatus;
import tech.kayys.syirkah.document.application.port.UploadSessionRepository;

import java.util.Optional;

@ApplicationScoped
public class PostgresUploadSessionRepository implements UploadSessionRepository {
    @Override
    public Uni<Optional<UploadSession>> findById(String tenantId, UploadSessionId uploadSessionId) {
        var tenantUuid = PostgresDocumentRepository.parseUuid(tenantId, "tenantId");
        var sessionUuid = PostgresDocumentRepository.parseUuid(uploadSessionId.value(), "uploadSessionId");
        return UploadSessionEntity.<UploadSessionEntity>find(
                        "tenantId = ?1 and id = ?2",
                        tenantUuid,
                        sessionUuid
                )
                .firstResult()
                .map(entity -> Optional.ofNullable(entity).map(this::toDomain));
    }

    @Override
    public Uni<Optional<UploadSession>> findByIdForUpdate(String tenantId, UploadSessionId uploadSessionId) {
        var tenantUuid = PostgresDocumentRepository.parseUuid(tenantId, "tenantId");
        var sessionUuid = PostgresDocumentRepository.parseUuid(uploadSessionId.value(), "uploadSessionId");
        return Panache.getSession()
                .chain(session -> session.createSelectionQuery(
                                "from UploadSessionEntity s where s.tenantId = :tenantId and s.id = :sessionId",
                                UploadSessionEntity.class
                        )
                        .setParameter("tenantId", tenantUuid)
                        .setParameter("sessionId", sessionUuid)
                        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                        .getSingleResultOrNull())
                .map(entity -> Optional.ofNullable(entity).map(this::toDomain));
    }

    @Override
    public Uni<Void> save(UploadSession session) {
        var tenantUuid = PostgresDocumentRepository.parseUuid(session.tenantId(), "tenantId");
        var sessionUuid = PostgresDocumentRepository.parseUuid(session.id().value(), "uploadSessionId");
        return UploadSessionEntity.<UploadSessionEntity>find(
                        "tenantId = ?1 and id = ?2",
                        tenantUuid,
                        sessionUuid
                )
                .firstResult()
                .chain(existing -> {
                    var isNew = existing == null;
                    var entity = isNew ? new UploadSessionEntity() : existing;
                    entity.id = sessionUuid;
                    entity.tenantId = tenantUuid;
                    entity.storageKey = session.storageKey();
                    entity.documentType = session.documentType().name();
                    entity.classification = session.classification().name();
                    entity.filename = session.metadata().filename();
                    entity.contentType = session.metadata().contentType();
                    entity.expectedSize = session.expectedSize();
                    entity.expectedSha256 = session.expectedSha256();
                    entity.customAttributes = session.metadata().customAttributes();
                    entity.expiresAt = session.expiresAt();
                    entity.status = session.status().name();
                    entity.completedDocumentId = session.completedDocumentId() == null
                            ? null
                            : PostgresDocumentRepository.parseUuid(
                                    session.completedDocumentId().value(), "completedDocumentId"
                            );
                    entity.completedVersionId = session.completedVersionId() == null
                            ? null
                            : PostgresDocumentRepository.parseUuid(
                                    session.completedVersionId().value(), "completedVersionId"
                            );
                    if (!isNew) {
                        return Uni.createFrom().voidItem();
                    }
                    return Panache.getSession().chain(sessionHandle -> sessionHandle.persist(entity));
                });
    }

    private UploadSession toDomain(UploadSessionEntity entity) {
        return UploadSession.reconstitute(
                new UploadSessionId(entity.id.toString()),
                entity.tenantId.toString(),
                entity.storageKey,
                DocumentType.valueOf(entity.documentType),
                DocumentClassification.valueOf(entity.classification),
                new DocumentMetadata(entity.filename, entity.contentType, entity.expectedSize, entity.customAttributes),
                entity.expectedSize,
                entity.expectedSha256,
                entity.expiresAt,
                UploadSessionStatus.valueOf(entity.status),
                entity.completedDocumentId == null ? null : DocumentId.of(entity.completedDocumentId.toString()),
                entity.completedVersionId == null
                        ? null
                        : DocumentVersionId.of(entity.completedVersionId.toString())
        );
    }
}
