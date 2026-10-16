package tech.kayys.syirkah.asset.domain.repository;

import tech.kayys.syirkah.asset.domain.identifier.AssetId;
import tech.kayys.syirkah.asset.domain.movement.AssetMovement;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Append-only port for asset movement history (see ASSET-13).
 *
 * <p>The projection is idempotent: {@code existsBySourceEvent} lets the
 * event consumer skip an event it has already applied.</p>
 */
public interface AssetMovementRepository {

    CompletionStage<Void> append(AssetMovement movement);

    CompletionStage<Boolean> existsBySourceEvent(UUID sourceEventId);

    CompletionStage<List<AssetMovement>> findByAsset(String tenantId, AssetId assetId);
}
