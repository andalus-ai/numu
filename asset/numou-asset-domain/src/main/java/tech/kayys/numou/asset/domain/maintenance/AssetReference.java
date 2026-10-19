package tech.kayys.numou.asset.domain.maintenance;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
public interface AssetReference {
    CompletionStage<Boolean> assetExists(String tenantId, UUID assetId);
}
