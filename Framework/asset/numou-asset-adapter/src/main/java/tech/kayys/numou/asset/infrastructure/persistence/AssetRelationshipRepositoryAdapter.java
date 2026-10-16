package tech.kayys.numou.asset.infrastructure.persistence;

import io.quarkus.hibernate.reactive.panache.Panache;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.numou.asset.domain.identifier.AssetId;
import tech.kayys.numou.asset.domain.relationship.AssetRelationship;
import tech.kayys.numou.asset.domain.relationship.AssetRelationshipId;
import tech.kayys.numou.asset.domain.repository.AssetRelationshipRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/**
 * Panache adapter for asset-to-asset relationships (ASSET-16).
 *
 * <p>Sessions/transactions are opened explicitly because the port contract is
 * {@link CompletionStage}-based (see {@link AssetRepositoryAdapter}).</p>
 */
@ApplicationScoped
public class AssetRelationshipRepositoryAdapter implements AssetRelationshipRepository {

    @Override
    public CompletionStage<AssetRelationship> save(AssetRelationship relationship) {
        AssetRelationshipEntity entity = new AssetRelationshipEntity();
        entity.id = relationship.id().value();
        entity.tenantId = relationship.tenantId();
        entity.sourceAssetId = relationship.sourceAssetId().value();
        entity.relatedAssetId = relationship.relatedAssetId().value();
        entity.relationshipType = relationship.type();
        entity.createdAt = relationship.createdAt();

        return Panache.withTransaction(() -> Panache.getSession()
                        .flatMap(session -> session.<AssetRelationshipEntity>merge(entity))
                        .replaceWith(relationship))
                .subscribe()
                .asCompletionStage();
    }

    @Override
    public CompletionStage<Optional<AssetRelationship>> findById(AssetRelationshipId id) {
        return Panache.withSession(() -> AssetRelationshipEntity.<AssetRelationshipEntity>findById(id.value())
                        .map(entity -> entity == null
                                ? Optional.<AssetRelationship>empty()
                                : Optional.of(toDomain(entity))))
                .subscribe()
                .asCompletionStage();
    }

    @Override
    public CompletionStage<List<AssetRelationship>> findBySource(String tenantId, AssetId sourceAssetId) {
        return Panache.withSession(() -> AssetRelationshipEntity.<AssetRelationshipEntity>list(
                        "tenantId = ?1 and sourceAssetId = ?2 order by relatedAssetId asc, relationshipType asc",
                        tenantId, sourceAssetId.value())
                        .map(entities -> entities.stream()
                                .map(AssetRelationshipRepositoryAdapter::toDomain)
                                .collect(Collectors.toList())))
                .subscribe()
                .asCompletionStage();
    }

    @Override
    public CompletionStage<List<AssetRelationship>> findByTarget(String tenantId, AssetId targetAssetId) {
        return Panache.withSession(() -> AssetRelationshipEntity.<AssetRelationshipEntity>list(
                        "tenantId = ?1 and relatedAssetId = ?2 order by sourceAssetId asc, relationshipType asc",
                        tenantId, targetAssetId.value())
                        .map(entities -> entities.stream()
                                .map(AssetRelationshipRepositoryAdapter::toDomain)
                                .collect(Collectors.toList())))
                .subscribe()
                .asCompletionStage();
    }

    @Override
    public CompletionStage<Void> delete(AssetRelationship relationship) {
        return Panache.withTransaction(() -> AssetRelationshipEntity.deleteById(relationship.id().value())
                        .replaceWith((Void) null))
                .subscribe()
                .asCompletionStage();
    }

    private static AssetRelationship toDomain(AssetRelationshipEntity entity) {
        return new AssetRelationship(
                AssetRelationshipId.of(entity.id),
                entity.tenantId,
                AssetId.of(entity.sourceAssetId),
                AssetId.of(entity.relatedAssetId),
                entity.relationshipType,
                entity.createdAt == null ? Instant.now() : entity.createdAt);
    }
}
