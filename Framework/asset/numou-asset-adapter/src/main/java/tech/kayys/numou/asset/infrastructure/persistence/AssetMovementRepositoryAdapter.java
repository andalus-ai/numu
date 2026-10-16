package tech.kayys.numou.asset.infrastructure.persistence;

import io.quarkus.hibernate.reactive.panache.Panache;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.numou.asset.domain.identifier.AssetId;
import tech.kayys.numou.asset.domain.movement.AssetMovement;
import tech.kayys.numou.asset.domain.movement.AssetMovementId;
import tech.kayys.numou.asset.domain.repository.AssetMovementRepository;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/**
 * Append-only Panache adapter for asset movement history (ASSET-13).
 *
 * <p>{@code append} never updates an existing row; {@code existsBySourceEvent}
 * lets the consumer skip an event it has already applied.</p>
 */
@ApplicationScoped
public class AssetMovementRepositoryAdapter implements AssetMovementRepository {

    @Override
    public CompletionStage<Void> append(AssetMovement movement) {
        AssetMovementEntity entity = toEntity(movement);
        return Panache.withTransaction(() -> Panache.getSession()
                        .flatMap(session -> session.persist(entity)))
                .subscribe()
                .asCompletionStage();
    }

    @Override
    public CompletionStage<Boolean> existsBySourceEvent(UUID sourceEventId) {
        return Panache.withSession(() -> AssetMovementEntity.count(
                        "sourceEventId = ?1", sourceEventId)
                        .map(count -> count > 0))
                .subscribe()
                .asCompletionStage();
    }

    @Override
    public CompletionStage<List<AssetMovement>> findByAsset(String tenantId, AssetId assetId) {
        return Panache.withSession(() -> AssetMovementEntity.<AssetMovementEntity>list(
                        "tenantId = ?1 and assetId = ?2 order by occurredAt asc, id asc",
                        tenantId, assetId.value())
                        .map(entities -> entities.stream()
                                .map(AssetMovementRepositoryAdapter::toDomain)
                                .collect(Collectors.toList())))
                .subscribe()
                .asCompletionStage();
    }

    private static AssetMovementEntity toEntity(AssetMovement movement) {
        AssetMovementEntity entity = new AssetMovementEntity();
        entity.id = movement.id().value();
        entity.tenantId = movement.tenantId();
        entity.assetId = movement.assetId().value();
        entity.movementType = movement.type();
        entity.occurredAt = movement.occurredAt();
        entity.fromLocationId = movement.fromLocationId();
        entity.fromPartyId = movement.fromPartyId();
        entity.toLocationId = movement.toLocationId();
        entity.toPartyId = movement.toPartyId();
        entity.classificationId = movement.classificationId();
        entity.relatedAssetId = movement.relatedAssetId();
        entity.sourceEventId = movement.sourceEventId();
        entity.createdAt = movement.occurredAt();
        return entity;
    }

    private static AssetMovement toDomain(AssetMovementEntity entity) {
        return new AssetMovement(
                AssetMovementId.of(entity.id),
                entity.tenantId,
                AssetId.of(entity.assetId),
                entity.movementType,
                entity.occurredAt,
                entity.fromLocationId,
                entity.fromPartyId,
                entity.toLocationId,
                entity.toPartyId,
                entity.classificationId,
                entity.relatedAssetId,
                entity.sourceEventId);
    }
}
