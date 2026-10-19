package tech.kayys.numou.asset.infrastructure.persistence;

import io.quarkus.hibernate.reactive.panache.Panache;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.numou.asset.domain.classification.AssetAttribute;
import tech.kayys.numou.asset.domain.identifier.AssetId;
import tech.kayys.numou.asset.domain.repository.AssetMetadataRepository;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/**
 * Panache adapter for dynamic asset metadata / attributes (ASSET-15).
 *
 * <p>Metadata lives outside the Asset aggregate so the aggregate does not turn
 * into a schema-less attribute bag. Writes are tenant-scoped.</p>
 */
@ApplicationScoped
public class AssetMetadataRepositoryAdapter implements AssetMetadataRepository {

    @Override
    public CompletionStage<Void> replaceAttributes(
            String tenantId,
            AssetId assetId,
            List<AssetAttribute> attributes
    ) {
        UUID id = assetId.value();
        return Panache.withTransaction(() ->
                AssetAttributeEntity.delete("tenantId = ?1 and assetId = ?2", tenantId, id)
                        .replaceWith(attributes == null || attributes.isEmpty()
                                ? io.smallrye.mutiny.Uni.createFrom().<Void>nullItem()
                                : persistAll(tenantId, id, attributes)))
                .subscribe()
                .asCompletionStage();
    }

    private static io.smallrye.mutiny.Uni<Void> persistAll(
            String tenantId, UUID assetId, List<AssetAttribute> attributes) {
        List<AssetAttributeEntity> rows = attributes.stream()
                .map(attribute -> {
                    AssetAttributeEntity row = new AssetAttributeEntity();
                    row.id = UUID.randomUUID();
                    row.tenantId = tenantId;
                    row.assetId = assetId;
                    row.attributeKey = attribute.key();
                    row.attributeValue = attribute.value();
                    row.attributeType = attribute.type();
                    return row;
                })
                .collect(Collectors.toList());
        return AssetAttributeEntity.persist(rows);
    }

    @Override
    public CompletionStage<List<AssetAttribute>> findByAsset(String tenantId, AssetId assetId) {
        return Panache.withSession(() -> AssetAttributeEntity.<AssetAttributeEntity>list(
                        "tenantId = ?1 and assetId = ?2 order by attributeKey",
                        tenantId, assetId.value())
                        .map(rows -> rows.stream()
                                .map(row -> AssetAttribute.of(
                                        row.attributeKey, row.attributeValue, row.attributeType))
                                .collect(Collectors.toList())))
                .subscribe()
                .asCompletionStage();
    }
}
