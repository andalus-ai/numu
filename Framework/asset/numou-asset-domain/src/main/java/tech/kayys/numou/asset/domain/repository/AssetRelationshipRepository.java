package tech.kayys.syirkah.asset.domain.repository;

import tech.kayys.syirkah.asset.domain.identifier.AssetId;
import tech.kayys.syirkah.asset.domain.relationship.AssetRelationship;
import tech.kayys.syirkah.asset.domain.relationship.AssetRelationshipId;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/** Port for asset-to-asset relationships (see ASSET-16). */
public interface AssetRelationshipRepository {

    CompletionStage<AssetRelationship> save(AssetRelationship relationship);

    CompletionStage<Optional<AssetRelationship>> findById(AssetRelationshipId id);

    CompletionStage<List<AssetRelationship>> findBySource(String tenantId, AssetId sourceAssetId);

    CompletionStage<List<AssetRelationship>> findByTarget(String tenantId, AssetId targetAssetId);

    CompletionStage<Void> delete(AssetRelationship relationship);
}
