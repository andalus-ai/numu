package tech.kayys.numou.asset.infrastructure.persistence;

import io.quarkus.hibernate.reactive.panache.Panache;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.numou.asset.domain.document.AssetDocumentReference;
import tech.kayys.numou.asset.domain.document.AssetDocumentReferenceId;
import tech.kayys.numou.asset.domain.identifier.AssetId;
import tech.kayys.numou.asset.domain.repository.AssetDocumentReferenceRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

@ApplicationScoped
public class AssetDocumentReferenceRepositoryAdapter implements AssetDocumentReferenceRepository {
    @Override
    public CompletionStage<AssetDocumentReference> save(AssetDocumentReference r) {
        AssetDocumentReferenceEntity e = toEntity(r);
        return Panache.withTransaction(() -> Panache.getSession()
                .flatMap(s -> s.<AssetDocumentReferenceEntity>merge(e)).replaceWith(r))
                .subscribe().asCompletionStage();
    }

    @Override
    public CompletionStage<Optional<AssetDocumentReference>> findById(String tenantId, AssetDocumentReferenceId id) {
        return Panache.withSession(() -> AssetDocumentReferenceEntity.<AssetDocumentReferenceEntity>findById(id.value())
                .map(e -> (e == null || !tenantId.equals(e.tenantId))
                        ? Optional.<AssetDocumentReference>empty() : Optional.of(toDomain(e))))
                .subscribe().asCompletionStage();
    }

    @Override
    public CompletionStage<List<AssetDocumentReference>> findByAsset(String tenantId, AssetId assetId) {
        return Panache.withSession(() -> AssetDocumentReferenceEntity.<AssetDocumentReferenceEntity>list(
                "tenantId = ?1 and assetId = ?2 order by linkedAt asc, id asc", tenantId, assetId.value())
                .map(list -> list.stream().map(AssetDocumentReferenceRepositoryAdapter::toDomain)
                        .collect(Collectors.toList()))).subscribe().asCompletionStage();
    }

    @Override
    public CompletionStage<List<AssetDocumentReference>> findExpiring(String tenantId, Instant now, Duration window) {
        Instant until = now.plus(window);
        return Panache.withSession(() -> AssetDocumentReferenceEntity.<AssetDocumentReferenceEntity>list(
                "tenantId = ?1 and validUntil >= ?2 and validUntil <= ?3 order by validUntil asc",
                tenantId, now, until)
                .map(list -> list.stream().map(AssetDocumentReferenceRepositoryAdapter::toDomain)
                        .collect(Collectors.toList()))).subscribe().asCompletionStage();
    }

    @Override
    public CompletionStage<Void> delete(String tenantId, AssetDocumentReferenceId id) {
        return Panache.withTransaction(() -> AssetDocumentReferenceEntity
                .delete("id = ?1 and tenantId = ?2", id.value(), tenantId).replaceWith((Void) null))
                .subscribe().asCompletionStage();
    }

    static AssetDocumentReferenceEntity toEntity(AssetDocumentReference r) {
        AssetDocumentReferenceEntity e = new AssetDocumentReferenceEntity();
        e.id = r.id().value();
        e.tenantId = r.tenantId();
        e.assetId = r.assetId().value();
        e.documentId = r.documentId();
        e.documentVersion = r.documentVersion();
        e.documentType = r.type();
        e.title = r.title();
        e.primaryDocument = r.primary();
        e.required = r.required();
        e.validFrom = r.validFrom();
        e.validUntil = r.validUntil();
        e.linkedAt = r.linkedAt();
        e.linkedBy = r.linkedBy();
        e.createdAt = r.linkedAt();
        return e;
    }

    static AssetDocumentReference toDomain(AssetDocumentReferenceEntity e) {
        return new AssetDocumentReference(AssetDocumentReferenceId.of(e.id), e.tenantId,
                AssetId.of(e.assetId), e.documentId, e.documentVersion, e.documentType, e.title,
                e.primaryDocument, e.required, e.validFrom, e.validUntil,
                e.linkedAt == null ? Instant.now() : e.linkedAt, e.linkedBy);
    }
}
