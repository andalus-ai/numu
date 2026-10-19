package tech.kayys.numou.asset.application.inspection;

import io.smallrye.mutiny.Uni;

public class SearchAssetInspectionsHandler {

    private final AssetInspectionReadRepository readRepository;

    public SearchAssetInspectionsHandler(AssetInspectionReadRepository readRepository) {
        this.readRepository = readRepository;
    }

    public Uni<AssetInspectionPage<AssetInspectionReadModel>> handle(AssetInspectionSearchCriteria criteria) {
        return Uni.createFrom().completionStage(() -> readRepository.search(criteria));
    }
}
