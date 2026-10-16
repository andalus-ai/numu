package tech.kayys.numou.asset.application.query;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.asset.domain.movement.AssetMovement;
import tech.kayys.numou.asset.domain.repository.AssetMovementRepository;

import java.util.List;
import java.util.Objects;

/**
 * Read-side handler for the movement history (ASSET-13).
 *
 * <p>Tenant-scoped: only movements recorded for the caller's tenant are
 * returned.</p>
 */
public class GetAssetMovementsHandler {

    private final AssetMovementRepository movements;

    public GetAssetMovementsHandler(AssetMovementRepository movements) {
        this.movements = Objects.requireNonNull(movements, "movements");
    }

    public Uni<List<AssetMovement>> handle(GetAssetMovementsQuery query) {
        return Uni.createFrom()
                .completionStage(() -> movements.findByAsset(
                        query.tenantId(), query.assetId()));
    }
}
