package tech.kayys.numou.asset.application.inspection;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Read-side port for inspections; implemented off the same entities (ASSET-20 section 20.22). */
public interface AssetInspectionReadRepository {

    CompletionStage<AssetInspectionPage<AssetInspectionReadModel>> search(AssetInspectionSearchCriteria criteria);

    CompletionStage<Optional<AssetInspectionReadModel>> findById(String tenantId, UUID inspectionId);

    CompletionStage<Optional<AssetConditionView>> findCurrentCondition(String tenantId, UUID assetId);
}
