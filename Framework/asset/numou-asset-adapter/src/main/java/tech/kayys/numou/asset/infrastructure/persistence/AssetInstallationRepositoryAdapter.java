package tech.kayys.numou.asset.infrastructure.persistence;

import io.quarkus.hibernate.reactive.panache.Panache;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.numou.asset.domain.installation.AssetInstallation;
import tech.kayys.numou.asset.domain.installation.AssetInstallationId;
import tech.kayys.numou.asset.domain.repository.AssetInstallationRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

@ApplicationScoped
public class AssetInstallationRepositoryAdapter implements AssetInstallationRepository {

    @Override
    public CompletionStage<AssetInstallation> save(AssetInstallation installation) {
        AssetInstallationEntity entity = toEntity(installation);
        return Panache.withTransaction(() -> Panache.getSession()
                        .flatMap(session -> session.<AssetInstallationEntity>merge(entity))
                        .replaceWith(installation))
                .subscribe()
                .asCompletionStage();
    }

    @Override
    public CompletionStage<Optional<AssetInstallation>> findById(String tenantId, AssetInstallationId id) {
        return Panache.withSession(() -> AssetInstallationEntity.<AssetInstallationEntity>findById(id.value())
                        .map(entity -> (entity == null || !tenantId.equals(entity.tenantId))
                                ? Optional.<AssetInstallation>empty()
                                : Optional.of(toDomain(entity))))
                .subscribe()
                .asCompletionStage();
    }

    @Override
    public CompletionStage<Optional<AssetInstallation>> findActive(String tenantId, UUID componentAssetId, UUID parentAssetId) {
        return Panache.withSession(() -> AssetInstallationEntity.<AssetInstallationEntity>list(
                        "tenantId = ?1 and componentAssetId = ?2 and parentAssetId = ?3 and status = ?4",
                        tenantId, componentAssetId, parentAssetId, tech.kayys.numou.asset.domain.installation.AssetInstallationStatus.INSTALLED)
                        .map(entities -> entities.stream().findFirst().map(AssetInstallationRepositoryAdapter::toDomain)))
                .subscribe()
                .asCompletionStage();
    }

    @Override
    public CompletionStage<List<AssetInstallation>> findHistory(String tenantId, UUID assetId) {
        return Panache.withSession(() -> AssetInstallationEntity.<AssetInstallationEntity>list(
                        "tenantId = ?1 and (componentAssetId = ?2 or parentAssetId = ?2) order by installedAt asc, id asc",
                        tenantId, assetId)
                        .map(entities -> entities.stream().map(AssetInstallationRepositoryAdapter::toDomain).collect(Collectors.toList())))
                .subscribe()
                .asCompletionStage();
    }

    @Override
    public CompletionStage<Boolean> existsActive(String tenantId, UUID componentAssetId) {
        return Panache.withSession(() -> AssetInstallationEntity.count(
                        "tenantId = ?1 and componentAssetId = ?2 and status = ?3",
                        tenantId, componentAssetId, tech.kayys.numou.asset.domain.installation.AssetInstallationStatus.INSTALLED)
                        .map(count -> count > 0))
                .subscribe()
                .asCompletionStage();
    }

    private static AssetInstallationEntity toEntity(AssetInstallation installation) {
        AssetInstallationEntity entity = new AssetInstallationEntity();
        entity.id = installation.id().value();
        entity.tenantId = installation.tenantId();
        entity.componentAssetId = installation.componentAssetId();
        entity.parentAssetId = installation.parentAssetId();
        entity.relationshipType = installation.relationshipType();
        entity.installedAt = installation.installedAt();
        entity.installedBy = installation.installedBy();
        entity.status = installation.status();
        entity.removedAt = installation.removedAt();
        entity.removedBy = installation.removedBy();
        entity.createdAt = installation.installedAt() == null ? Instant.now() : installation.installedAt();
        return entity;
    }

    private static AssetInstallation toDomain(AssetInstallationEntity entity) {
        return new AssetInstallation(AssetInstallationId.of(entity.id), entity.tenantId, entity.componentAssetId,
                entity.parentAssetId, entity.relationshipType, entity.installedAt, entity.installedBy,
                entity.status, entity.removedAt, entity.removedBy);
    }
}
