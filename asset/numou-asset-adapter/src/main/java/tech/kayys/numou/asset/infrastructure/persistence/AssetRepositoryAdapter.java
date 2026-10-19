package tech.kayys.numou.asset.infrastructure.persistence;

import io.quarkus.hibernate.reactive.panache.Panache;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.numou.asset.domain.identifier.AssetId;
import tech.kayys.numou.asset.domain.model.Asset;
import tech.kayys.numou.asset.domain.repository.AssetRepository;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Hibernate Reactive / Panache adapter for the tenant-aware Asset port (ASSET-09).
 *
 * <p>Sessions and transactions are opened explicitly via
 * {@link Panache#withSession}/{@link Panache#withTransaction} rather than with
 * the {@code @WithSession}/{@code @WithTransaction} interceptors, because the
 * port contract is {@link CompletionStage}-based while the interceptors require
 * a {@code Uni} return type.</p>
 */
@ApplicationScoped
public class AssetRepositoryAdapter implements AssetRepository {

    @Override
    public CompletionStage<Asset> save(Asset asset) {
        AssetEntity entity = AssetEntityMapper.toEntity(asset);
        return Panache.withTransaction(() -> Panache.getSession()
                        .flatMap(session -> session.<AssetEntity>merge(entity))
                        .replaceWith(asset))
                .subscribe()
                .asCompletionStage();
    }

    @Override
    public CompletionStage<Optional<Asset>> findById(AssetId id) {
        return Panache.withSession(() -> AssetEntity.<AssetEntity>findById(id.value())
                        .map(entity -> entity == null
                                ? Optional.<Asset>empty()
                                : Optional.of(AssetEntityMapper.toDomain(entity))))
                .subscribe()
                .asCompletionStage();
    }

    @Override
    public CompletionStage<Boolean> existsById(AssetId id) {
        return Panache.withSession(() -> AssetEntity.<AssetEntity>findById(id.value())
                        .map(entity -> entity != null))
                .subscribe()
                .asCompletionStage();
    }

    @Override
    public CompletionStage<Void> delete(Asset aggregate) {
        return deleteById(aggregate.id());
    }

    @Override
    public CompletionStage<Void> deleteById(AssetId id) {
        return Panache.withTransaction(() -> AssetEntity.deleteById(id.value())
                        .replaceWith((Void) null))
                .subscribe()
                .asCompletionStage();
    }

    @Override
    public CompletionStage<Boolean> existsByAssetNumber(String tenantId, String assetNumber) {
        return Panache.withSession(() -> AssetEntity.count(
                        "tenantId = ?1 and assetNumber = ?2", tenantId, assetNumber)
                        .map(count -> count > 0))
                .subscribe()
                .asCompletionStage();
    }

    @Override
    public CompletionStage<Optional<Asset>> findByTenantAndId(String tenantId, AssetId assetId) {
        return Panache.withSession(() -> AssetEntity.<AssetEntity>find(
                        "tenantId = ?1 and id = ?2", tenantId, assetId.value())
                        .firstResult()
                        .map(entity -> entity == null
                                ? Optional.<Asset>empty()
                                : Optional.of(AssetEntityMapper.toDomain(entity))))
                .subscribe()
                .asCompletionStage();
    }

    @Override
    public CompletionStage<Boolean> existsByTenantAndId(String tenantId, AssetId assetId) {
        return Panache.withSession(() -> AssetEntity.count(
                        "tenantId = ?1 and id = ?2", tenantId, assetId.value())
                        .map(count -> count > 0))
                .subscribe()
                .asCompletionStage();
    }

    @Override
    public CompletionStage<Void> deleteByTenantAndId(String tenantId, AssetId assetId) {
        return Panache.withTransaction(() -> AssetEntity.delete(
                        "tenantId = ?1 and id = ?2", tenantId, assetId.value())
                        .replaceWith((Void) null))
                .subscribe()
                .asCompletionStage();
    }
}
