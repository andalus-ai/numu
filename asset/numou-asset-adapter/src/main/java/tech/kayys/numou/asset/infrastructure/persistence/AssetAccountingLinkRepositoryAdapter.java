package tech.kayys.numou.asset.infrastructure.persistence;

import io.quarkus.hibernate.reactive.panache.Panache;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.numou.asset.domain.finance.AssetAccountingLink;
import tech.kayys.numou.asset.domain.finance.AssetAccountingLinkId;
import tech.kayys.numou.asset.domain.identifier.AssetId;
import tech.kayys.numou.asset.domain.repository.AssetAccountingLinkRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/** Panache adapter for the asset→accounting link (ASSET-25). Explicit sessions. */
@ApplicationScoped
public class AssetAccountingLinkRepositoryAdapter implements AssetAccountingLinkRepository {

    @Override
    public CompletionStage<AssetAccountingLink> save(AssetAccountingLink link) {
        AssetAccountingLinkEntity entity = toEntity(link);
        return Panache.withTransaction(() -> Panache.getSession()
                        .flatMap(session -> session.<AssetAccountingLinkEntity>merge(entity))
                        .replaceWith(link))
                .subscribe().asCompletionStage();
    }

    @Override
    public CompletionStage<Optional<AssetAccountingLink>> findByAsset(String tenantId, AssetId assetId) {
        return Panache.withSession(() -> AssetAccountingLinkEntity
                        .<AssetAccountingLinkEntity>find(
                                "tenantId = ?1 and assetId = ?2 and linkActive = true",
                                tenantId, assetId.value())
                        .firstResult()
                        .map(entity -> Optional.ofNullable(entity).map(
                                AssetAccountingLinkRepositoryAdapter::toDomain)))
                .subscribe().asCompletionStage();
    }

    @Override
    public CompletionStage<Optional<AssetAccountingLink>> findByAccountingAsset(
            String tenantId, UUID accountingAssetId) {
        return Panache.withSession(() -> AssetAccountingLinkEntity
                        .<AssetAccountingLinkEntity>find(
                                "tenantId = ?1 and accountingAssetId = ?2 and linkActive = true",
                                tenantId, accountingAssetId)
                        .firstResult()
                        .map(entity -> Optional.ofNullable(entity).map(
                                AssetAccountingLinkRepositoryAdapter::toDomain)))
                .subscribe().asCompletionStage();
    }

    @Override
    public CompletionStage<Optional<AssetAccountingLink>> findById(
            String tenantId, AssetAccountingLinkId id) {
        return Panache.withSession(() -> AssetAccountingLinkEntity
                        .<AssetAccountingLinkEntity>find("tenantId = ?1 and id = ?2",
                                tenantId, id.value())
                        .firstResult()
                        .map(entity -> Optional.ofNullable(entity).map(
                                AssetAccountingLinkRepositoryAdapter::toDomain)))
                .subscribe().asCompletionStage();
    }

    @Override
    public CompletionStage<List<AssetAccountingLink>> findAll(String tenantId) {
        return Panache.withSession(() -> AssetAccountingLinkEntity
                        .<AssetAccountingLinkEntity>list(
                                "tenantId = ?1 order by linkedAt desc, id desc", tenantId)
                        .map(rows -> rows.stream()
                                .map(AssetAccountingLinkRepositoryAdapter::toDomain)
                                .collect(Collectors.toList())))
                .subscribe().asCompletionStage();
    }

    private static AssetAccountingLinkEntity toEntity(AssetAccountingLink link) {
        AssetAccountingLinkEntity entity = new AssetAccountingLinkEntity();
        entity.id = link.id().value();
        entity.tenantId = link.tenantId();
        entity.assetId = link.assetId().value();
        entity.accountingAssetId = link.accountingAssetId();
        entity.accountingAssetNumber = link.accountingAssetNumber();
        entity.linkedAt = link.linkedAt();
        entity.linkedBy = link.linkedBy();
        entity.linkActive = link.active();
        return entity;
    }

    private static AssetAccountingLink toDomain(AssetAccountingLinkEntity entity) {
        return new AssetAccountingLink(
                AssetAccountingLinkId.of(entity.id), entity.tenantId,
                AssetId.of(entity.assetId), entity.accountingAssetId,
                entity.accountingAssetNumber,
                entity.linkedAt == null ? Instant.now() : entity.linkedAt,
                entity.linkedBy, entity.linkActive);
    }
}