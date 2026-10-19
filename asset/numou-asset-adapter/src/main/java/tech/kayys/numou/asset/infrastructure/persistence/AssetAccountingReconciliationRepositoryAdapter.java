package tech.kayys.numou.asset.infrastructure.persistence;

import io.quarkus.hibernate.reactive.panache.Panache;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.numou.asset.domain.finance.AssetAccountingReconciliation;
import tech.kayys.numou.asset.domain.identifier.AssetId;
import tech.kayys.numou.asset.domain.repository.AssetAccountingReconciliationRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/** Panache adapter for reconciliation entries (ASSET-25 §34). */
@ApplicationScoped
public class AssetAccountingReconciliationRepositoryAdapter
        implements AssetAccountingReconciliationRepository {

    @Override
    public CompletionStage<AssetAccountingReconciliation> save(AssetAccountingReconciliation entry) {
        AssetAccountingReconciliationEntity entity = toEntity(entry);
        return Panache.withTransaction(() -> Panache.getSession()
                        .flatMap(session -> session.<AssetAccountingReconciliationEntity>merge(entity))
                        .replaceWith(entry))
                .subscribe().asCompletionStage();
    }

    @Override
    public CompletionStage<List<AssetAccountingReconciliation>> findByAsset(
            String tenantId, AssetId assetId) {
        return Panache.withSession(() -> AssetAccountingReconciliationEntity
                        .<AssetAccountingReconciliationEntity>list(
                                "tenantId = ?1 and assetId = ?2 order by reconciledAt desc, id desc",
                                tenantId, assetId.value())
                        .map(rows -> rows.stream()
                                .map(AssetAccountingReconciliationRepositoryAdapter::toDomain)
                                .collect(Collectors.toList())))
                .subscribe().asCompletionStage();
    }

    @Override
    public CompletionStage<List<AssetAccountingReconciliation>> findAll(String tenantId) {
        return Panache.withSession(() -> AssetAccountingReconciliationEntity
                        .<AssetAccountingReconciliationEntity>list(
                                "tenantId = ?1 order by reconciledAt desc, id desc", tenantId)
                        .map(rows -> rows.stream()
                                .map(AssetAccountingReconciliationRepositoryAdapter::toDomain)
                                .collect(Collectors.toList())))
                .subscribe().asCompletionStage();
    }

    @Override
    public CompletionStage<Optional<AssetAccountingReconciliation>> findLatestByAsset(
            String tenantId, AssetId assetId) {
        return Panache.withSession(() -> AssetAccountingReconciliationEntity
                        .<AssetAccountingReconciliationEntity>find(
                                "tenantId = ?1 and assetId = ?2 order by reconciledAt desc, id desc",
                                tenantId, assetId.value())
                        .firstResult()
                        .map(entity -> Optional.ofNullable(entity).map(
                                AssetAccountingReconciliationRepositoryAdapter::toDomain)))
                .subscribe().asCompletionStage();
    }

    private static AssetAccountingReconciliationEntity toEntity(AssetAccountingReconciliation r) {
        AssetAccountingReconciliationEntity e = new AssetAccountingReconciliationEntity();
        e.id = r.id() == null ? UUID.randomUUID() : r.id();
        e.tenantId = r.tenantId();
        e.assetId = r.assetId().value();
        e.accountingAssetId = r.accountingAssetId();
        e.reconciliationStatus = r.status();
        e.detail = r.detail();
        e.reconciledAt = r.reconciledAt();
        e.reconciledBy = r.reconciledBy();
        return e;
    }

    private static AssetAccountingReconciliation toDomain(AssetAccountingReconciliationEntity e) {
        return new AssetAccountingReconciliation(
                e.id, e.tenantId, AssetId.of(e.assetId), e.accountingAssetId,
                e.reconciliationStatus, e.detail, e.reconciledAt, e.reconciledBy);
    }
}