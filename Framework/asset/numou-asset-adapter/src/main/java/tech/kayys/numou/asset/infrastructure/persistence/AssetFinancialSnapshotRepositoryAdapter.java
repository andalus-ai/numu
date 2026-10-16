package tech.kayys.numou.asset.infrastructure.persistence;

import io.quarkus.hibernate.reactive.panache.Panache;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.numou.asset.domain.finance.AssetFinancialSnapshot;
import tech.kayys.numou.asset.domain.identifier.AssetId;
import tech.kayys.numou.asset.domain.repository.AssetFinancialSnapshotRepository;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

/** Panache adapter for the financial-snapshot projection (ASSET-25 §26). */
@ApplicationScoped
public class AssetFinancialSnapshotRepositoryAdapter implements AssetFinancialSnapshotRepository {

    @Override
    public CompletionStage<AssetFinancialSnapshot> save(
            String tenantId, AssetFinancialSnapshot snapshot, String sourceEventKey, String sourceKind) {
        AssetFinancialSnapshotEntity entity = toEntity(tenantId, snapshot, sourceEventKey, sourceKind);
        // One projection row per (tenant, asset): replace the previous snapshot deterministically.
        return Panache.withSession(() -> AssetFinancialSnapshotEntity
                        .<AssetFinancialSnapshotEntity>find(
                                "tenantId = ?1 and assetId = ?2", tenantId, snapshot.assetId().value())
                        .firstResult()
                        .map(existing -> {
                            if (existing != null) {
                                entity.id = existing.id;
                                entity.createdAt = existing.createdAt;
                            } else {
                                entity.id = java.util.UUID.randomUUID();
                            }
                            return entity;
                        }))
                .flatMap(prepared -> Panache.withTransaction(() -> Panache.getSession()
                        .flatMap(session -> session.<AssetFinancialSnapshotEntity>merge(prepared))
                        .replaceWith(snapshot)))
                .subscribe().asCompletionStage();
    }

    @Override
    public CompletionStage<Optional<AssetFinancialSnapshot>> findByAsset(String tenantId, AssetId assetId) {
        return Panache.withSession(() -> AssetFinancialSnapshotEntity
                        .<AssetFinancialSnapshotEntity>find(
                                "tenantId = ?1 and assetId = ?2", tenantId, assetId.value())
                        .firstResult()
                        .map(entity -> Optional.ofNullable(entity).map(
                                AssetFinancialSnapshotRepositoryAdapter::toDomain)))
                .subscribe().asCompletionStage();
    }

    @Override
    public CompletionStage<Boolean> existsBySourceEvent(String tenantId, String sourceEventKey) {
        return Panache.withSession(() -> AssetFinancialSnapshotEntity.count(
                        "tenantId = ?1 and sourceEventKey = ?2", tenantId, sourceEventKey)
                        .map(count -> count > 0))
                .subscribe().asCompletionStage();
    }

    private static AssetFinancialSnapshotEntity toEntity(
            String tenantId, AssetFinancialSnapshot s, String sourceEventKey, String sourceKind) {
        AssetFinancialSnapshotEntity e = new AssetFinancialSnapshotEntity();
        e.tenantId = tenantId;
        e.assetId = s.assetId().value();
        e.accountingAssetId = s.accountingAssetId();
        e.acquisitionCost = s.acquisitionCost();
        e.accumulatedDepreciation = s.accumulatedDepreciation();
        e.netBookValue = s.netBookValue();
        e.impairmentAmount = s.impairmentAmount();
        e.proceeds = s.proceeds();
        e.currency = s.currency();
        e.capitalizationDate = s.capitalizationDate();
        e.lastDepreciationDate = s.lastDepreciationDate();
        e.disposedAt = s.disposedAt();
        e.fixedAssetStatus = s.status();
        e.asOf = s.asOf();
        e.sourceEventKey = sourceEventKey;
        e.sourceKind = sourceKind;
        return e;
    }

    private static AssetFinancialSnapshot toDomain(AssetFinancialSnapshotEntity e) {
        return new AssetFinancialSnapshot(
                AssetId.of(e.assetId), e.accountingAssetId, e.acquisitionCost,
                e.accumulatedDepreciation, e.netBookValue, e.impairmentAmount, e.proceeds,
                e.currency, e.capitalizationDate, e.lastDepreciationDate, e.disposedAt,
                e.fixedAssetStatus, e.asOf);
    }
}