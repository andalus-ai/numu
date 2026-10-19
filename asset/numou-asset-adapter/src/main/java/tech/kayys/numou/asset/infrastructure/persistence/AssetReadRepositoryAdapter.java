package tech.kayys.numou.asset.infrastructure.persistence;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.numou.asset.application.query.AssetPage;
import tech.kayys.numou.asset.application.query.AssetReadRepository;
import tech.kayys.numou.asset.application.query.AssetSearchCriteria;
import tech.kayys.numou.asset.application.query.AssetView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/**
 * Read-side (query) adapter — deliberately separate from the write-side
 * {@link AssetRepositoryAdapter} (see ASSET-14).
 *
 * <p>Every query is tenant-scoped: {@code tenantId = :tenant} is always part of
 * the predicate, never a bare {@code WHERE id = ?}.</p>
 */
@ApplicationScoped
public class AssetReadRepositoryAdapter implements AssetReadRepository {

    @Override
    public CompletionStage<Optional<AssetView>> findById(String tenantId, UUID assetId) {
        Map<String, Object> params = new HashMap<>();
        params.put("tenant", tenantId);
        params.put("id", assetId);

        return Panache.withSession(() -> AssetEntity.<AssetEntity>find(
                        "tenantId = :tenant and id = :id", params)
                        .firstResult()
                        .map(entity -> entity == null
                                ? Optional.<AssetView>empty()
                                : Optional.of(toView(entity))))
                .subscribe()
                .asCompletionStage();
    }

    @Override
    public CompletionStage<AssetPage<AssetView>> search(AssetSearchCriteria criteria) {
        StringBuilder where = new StringBuilder("tenantId = :tenant");
        Map<String, Object> params = new HashMap<>();
        params.put("tenant", criteria.tenantId());

        if (criteria.status() != null) {
            where.append(" and status = :status");
            params.put("status", criteria.status());
        }
        if (criteria.type() != null) {
            where.append(" and assetType = :type");
            params.put("type", criteria.type());
        }
        if (criteria.locationId() != null) {
            where.append(" and locationId = :locationId");
            params.put("locationId", criteria.locationId());
        }
        if (criteria.partyId() != null) {
            where.append(" and partyId = :partyId");
            params.put("partyId", criteria.partyId());
        }

        String whereClause = where.toString();
        String ordered = whereClause + " order by assetNumber asc, id asc";

        return Panache.withSession(() -> AssetEntity.count(whereClause, params)
                        .flatMap(total -> total == 0
                                ? Uni.createFrom().item(
                                        AssetPage.<AssetView>of(List.of(), criteria.page(), criteria.size(), 0L))
                                : AssetEntity.<AssetEntity>find(ordered, params)
                                        .page(criteria.page(), criteria.size())
                                        .list()
                                        .map(items -> AssetPage.of(
                                                items.stream()
                                                        .map(AssetReadRepositoryAdapter::toView)
                                                        .collect(Collectors.toList()),
                                                criteria.page(), criteria.size(), total))))
                .subscribe()
                .asCompletionStage();
    }

    private static AssetView toView(AssetEntity entity) {
        return AssetView.from(AssetEntityMapper.toDomain(entity));
    }
}
